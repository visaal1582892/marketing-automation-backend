package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class WorkTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Minimal FROM + JOINs shared by both SELECT_BASE and count queries. */
    private static final String FROM_JOINS = """
            FROM work_tasks wt
            LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
            LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
            LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
            LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
            LEFT JOIN users             req ON req.user_id            = c.requestor_id
            """;

    private static final String COUNT_BASE = "SELECT COUNT(*) " + FROM_JOINS;

    /** Newest activity first; approval-log id breaks ties when timestamps match. */
    private static final String ORDER_BY_LAST_MODIFIED = """
             ORDER BY COALESCE(wt.updated_at, wt.created_at) DESC,
                      (SELECT COALESCE(MAX(al.log_id), 0)
                         FROM approvals_log al
                        WHERE al.task_id = wt.task_id) DESC,
                      CAST(SUBSTRING(wt.task_id, 11) AS UNSIGNED) DESC
            """;

    private static final String SELECT_BASE = """
            SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                   wt.granular_task_id,
                   wt.status,
                   wt.is_collaboration_started,
                   wt.is_collaboration_active,
                   wt.assigned_at, wt.accepted_at, wt.started_at,
                   wt.submitted_at, wt.manager_approved_at, wt.requestor_approved_at,
                   wt.total_time_logged_minutes, wt.dynamic_deadline,
                   wt.submission_notes,
                   wt.created_at, wt.updated_at,
                   u.full_name    AS assignee_name,
                   gt.task_name  AS granular_task_name,
                   tt.task_name  AS task_type_name,
                   c.deadline      AS campaign_deadline,
                   c.priority      AS campaign_priority,
                   c.status        AS campaign_status,
                   c.requestor_id,
                   c.store_id      AS campaign_store_id,
                   c.contact_number AS campaign_contact_number,
                   req.full_name   AS requestor_name,
                   (SELECT COUNT(*) FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'NEEDS_REWORK') AS rework_count,
                   (SELECT COUNT(*) FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'REQUESTOR_REWORK') AS requestor_rework_count,
                   (SELECT al.comments FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'NEEDS_REWORK'
                     ORDER BY al.created_at DESC LIMIT 1) AS latest_manager_rework_comment,
                   (SELECT al.comments FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'REQUESTOR_REWORK'
                     ORDER BY al.created_at DESC LIMIT 1) AS latest_requestor_rework_comment,
                   (SELECT al.comments FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken IN ('NEEDS_REWORK','REQUESTOR_REWORK')
                     ORDER BY al.created_at DESC LIMIT 1) AS latest_rework_comment,
                   (SELECT al.action_taken FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken IN ('NEEDS_REWORK','REQUESTOR_REWORK')
                     ORDER BY al.created_at DESC LIMIT 1) AS latest_rework_source,
                   (SELECT u_adb.full_name FROM approvals_log al_adb
                     LEFT JOIN users u_adb ON u_adb.user_id = al_adb.reviewer_id
                     WHERE al_adb.task_id = wt.task_id
                     ORDER BY al_adb.log_id DESC LIMIT 1) AS latest_action_done_by_name,
                   (SELECT COUNT(*) > 0 FROM worker_comments wc
                     WHERE wc.task_id = wt.task_id
                       AND wc.is_answered = 0) AS has_active_comments
            """ + FROM_JOINS;

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Inserts a new work task. Generates the next {@code WORK-TASK-X} string
     * primary key before the INSERT (NamedParameterJdbcTemplate — no JPA).
     *
     * @return the generated task ID, e.g. {@code "WORK-TASK-7"}
     */
    public String insert(WorkTask t) {
        String newId = generateNextTaskId();
        String sql = """
                INSERT INTO work_tasks
                    (task_id, campaign_id, assigned_to, granular_task_id,
                     status, dynamic_deadline, assigned_at)
                VALUES
                    (:taskId, :campaignId, :assignedTo, :granularTaskId,
                     :status, :dynamicDeadline, :assignedAt)
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("taskId",          newId)
                .addValue("campaignId",      t.getCampaignId())
                .addValue("assignedTo",      t.getAssignedTo())
                .addValue("granularTaskId",  t.getGranularTaskId())
                .addValue("status",          t.getStatus() == null ? "ASSIGNED" : t.getStatus().name())
                .addValue("dynamicDeadline", t.getDynamicDeadline())
                .addValue("assignedAt",      Timestamp.valueOf(LocalDateTime.now()));
        jdbc.update(sql, p);
        return newId;
    }

    public int updateStatus(String taskId, TaskStatus status) {
        return jdbc.update(
                "UPDATE work_tasks SET status = :status WHERE task_id = :id",
                new MapSqlParameterSource("status", status.name()).addValue("id", taskId));
    }

    /**
     * Marks a task as IN_PROGRESS (accepted + timer started).
     *
     * <p>{@code accepted_at} is set to the FIRST acceptance time only — on
     * REWORK rounds it is preserved (we use {@code COALESCE}). This way the
     * audit trail keeps the canonical "when did the worker first take this
     * up" timestamp, while {@code started_at} is reset every cycle so the
     * live timer measures only the current rework session.
     */
    public int accept(String taskId, int userId) {
        LocalDateTime now = LocalDateTime.now();
        return jdbc.update("""
                UPDATE work_tasks
                   SET status      = 'IN_PROGRESS',
                       accepted_at = COALESCE(accepted_at, :now),
                       started_at  = :now
                 WHERE task_id     = :id
                   AND assigned_to = :userId
                   AND status IN ('ASSIGNED', 'REWORK')
                """,
                new MapSqlParameterSource("now", Timestamp.valueOf(now))
                        .addValue("id",     taskId)
                        .addValue("userId", userId));
    }

    /**
     * Marks a task as MANAGER_QC_REVIEW and logs time spent + creator's submission.
     * Stores the submission timestamp in `submitted_at` (not `manager_approved_at` —
     * `manager_approved_at` is reserved for the QC manager's approval timestamp).
     */
    public int complete(String taskId, int userId, LocalDateTime submittedAt, int minutesSpent,
                        String submissionNotes) {
        return jdbc.update("""
                UPDATE work_tasks
                SET status = 'MANAGER_QC_REVIEW',
                    submitted_at = :submittedAt,
                    total_time_logged_minutes = :minutes,
                    submission_notes = :notes
                WHERE task_id = :id
                  AND assigned_to = :userId
                  AND status = 'IN_PROGRESS'
                """,
                new MapSqlParameterSource("submittedAt", Timestamp.valueOf(submittedAt))
                        .addValue("minutes", minutesSpent)
                        .addValue("notes",   submissionNotes)
                        .addValue("id",      taskId)
                        .addValue("userId",  userId));
    }

    /** Auto-created content tasks skip both QC stages and complete directly. */
    public int completeAutoAssigned(String taskId, int userId, LocalDateTime completedAt,
                                    int minutesSpent, String submissionNotes) {
        return jdbc.update("""
                UPDATE work_tasks
                SET status = 'COMPLETED',
                    submitted_at = :completedAt,
                    manager_approved_at = :completedAt,
                    requestor_approved_at = :completedAt,
                    total_time_logged_minutes = :minutes,
                    submission_notes = :notes,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE task_id = :id
                  AND assigned_to = :userId
                  AND status = 'IN_PROGRESS'
                """,
                new MapSqlParameterSource("completedAt", Timestamp.valueOf(completedAt))
                        .addValue("minutes", minutesSpent)
                        .addValue("notes",   submissionNotes)
                        .addValue("id",      taskId)
                        .addValue("userId",  userId));
    }

    /**
     * Manager approved the task — moves to REQUESTOR_QC_REVIEW. Stamps manager_approved_at = now.
     * Worker's capacity slot is freed at this point.
     */
    public int markManagerApproved(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status = 'REQUESTOR_QC_REVIEW',
                       manager_approved_at = :now
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Requestor approved the task — moves to COMPLETED. Stamps requestor_approved_at = now.
     */
    public int markRequestorApproved(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status = 'COMPLETED',
                       requestor_approved_at = :now
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Marks the task as CANCELLED — used when sibling tasks on a campaign are
     * swept because another task was QC-rejected, or when the parent campaign
     * is rejected before this task could be finished.
     */
    public int markCancelled(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status = 'CANCELLED'
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Marks the task as REJECTED — used exclusively when a QC reviewer
     * rejects this specific task outright. Sibling tasks swept as a result
     * are set to CANCELLED (via {@link #cancelOpenTasksForCampaign}), not
     * REJECTED, to distinguish the direct cause from the knock-on effect.
     */
    public int markRejected(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status = 'REJECTED',
                       updated_at = CURRENT_TIMESTAMP(6)
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /** Bumps {@code updated_at} so the row sorts ahead of batch-updated siblings. */
    public int touchUpdatedAt(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET updated_at = CURRENT_TIMESTAMP(6)
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Sends the task back for rework. Clears {@code submitted_at} and
     * {@code manager_approved_at} so the lifecycle timeline accurately reflects the
     * current cycle. Historical timestamps live in {@code approval_logs}.
     */
    public int markRework(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status              = 'REWORK',
                       submitted_at        = NULL,
                       manager_approved_at = NULL,
                       started_at          = CURRENT_TIMESTAMP(6)
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Holds an ASSIGNED task — pulls it out of the assignee's queue so their
     * capacity slot can be redirected to a higher-priority job. The original
     * {@code assigned_to} is preserved for audit so the manager can see who
     * was holding it before; the unhold flow re-routes via the engine and
     * may pick a different user.
     *
     * <p>Returns 0 if the task is not in ASSIGNED state — by design we refuse
     * to hold IN_PROGRESS / REWORK / QC_REVIEW work because the worker has
     * already invested time and ripping it out is destructive.
     */
    public int markHeld(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET pre_hold_status = status,
                       status          = 'HELD'
                 WHERE task_id = :id
                   AND status IN ('ASSIGNED', 'REWORK')
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Re-assigns a HELD task to a (potentially different) user and restores
     * {@code pre_hold_status} (ASSIGNED, REWORK, etc.).
     *
     * <p>When the pre-hold state was IN_PROGRESS, we resume as ASSIGNED so the
     * newly assigned user explicitly starts the task. In that case acceptance
     * timestamps are cleared.
     */
    public int reassignFromHeld(String taskId, int newAssigneeId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status          = CASE
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') = 'IN_PROGRESS' THEN 'ASSIGNED'
                                            ELSE COALESCE(pre_hold_status, 'ASSIGNED')
                                         END,
                       pre_hold_status = NULL,
                       assigned_to     = :uid,
                       accepted_at     = IF(COALESCE(pre_hold_status, 'ASSIGNED') IN ('ASSIGNED','IN_PROGRESS'), NULL, accepted_at),
                       started_at      = IF(COALESCE(pre_hold_status, 'ASSIGNED') IN ('ASSIGNED','IN_PROGRESS'), NULL, started_at)
                 WHERE task_id = :id
                   AND status  = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", newAssigneeId));
    }

    /**
     * Flips the task to HELD and captures the pre-hold status so it can be
     * restored exactly on unhold. Worker comments are now stored in the
     * separate worker_comments table (see WorkerCommentRepository).
     * Allowed from ASSIGNED, IN_PROGRESS, or REWORK.
     */
    public int holdTask(String taskId, int userId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET pre_hold_status = status,
                       status          = 'HELD'
                 WHERE task_id     = :id
                   AND assigned_to = :uid
                   AND status IN ('ASSIGNED','IN_PROGRESS','REWORK')
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", userId));
    }

    /**
     * Resumes a HELD task, restoring the status it had before being held
     * (captured in pre_hold_status). Falls back to ASSIGNED if NULL.
     * Called by the worker when they decide to resume work.
     */
    public int clearHold(String taskId, int userId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status          = CASE
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') IN ('ASSIGNED','IN_PROGRESS','REWORK')
                                              THEN 'IN_PROGRESS'
                                            ELSE COALESCE(pre_hold_status, 'ASSIGNED')
                                         END,
                       accepted_at     = COALESCE(accepted_at, CURRENT_TIMESTAMP(6)),
                       started_at      = COALESCE(started_at, accepted_at, CURRENT_TIMESTAMP(6)),
                       pre_hold_status = NULL
                 WHERE task_id     = :id
                   AND assigned_to = :uid
                   AND status      = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", userId));
    }

    /**
     * Resumes a HELD task by task ID only — called when all worker comments are answered,
     * implicitly clearing the hold without any worker action.
     * No userId guard (caller is the requestor or assignee, not necessarily the worker).
     *
     * Restores exactly to pre_hold_status:
     *   - pre_hold_status = ASSIGNED  → back to ASSIGNED; accepted_at/started_at untouched
     *     (worker has not accepted yet; auto-answering a comment must not start the timer)
     *   - pre_hold_status = IN_PROGRESS or REWORK → back to IN_PROGRESS; stamps timestamps
     *     only if not already set (task was actively running before hold)
     */
    public int clearHoldByTaskId(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status          = CASE
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') = 'ASSIGNED'
                                              THEN 'ASSIGNED'
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') IN ('IN_PROGRESS','REWORK')
                                              THEN 'IN_PROGRESS'
                                            ELSE COALESCE(pre_hold_status, 'ASSIGNED')
                                         END,
                       accepted_at     = CASE
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') = 'ASSIGNED'
                                              THEN accepted_at
                                            ELSE COALESCE(accepted_at, CURRENT_TIMESTAMP(6))
                                         END,
                       started_at      = CASE
                                            WHEN COALESCE(pre_hold_status, 'ASSIGNED') = 'ASSIGNED'
                                              THEN started_at
                                            ELSE COALESCE(started_at, accepted_at, CURRENT_TIMESTAMP(6))
                                         END,
                       pre_hold_status = NULL
                 WHERE task_id = :id
                   AND status  = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId));
    }

    /**
     * Holds a task for collaboration — works from any active status
     * (ASSIGNED, ACCEPTED, IN_PROGRESS, REWORK, MANAGER_QC_REVIEW).
     * Saves the current status in pre_hold_status so it can be restored on resume.
     */
    public int holdForCollaboration(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET pre_hold_status = status,
                       status          = 'HELD'
                 WHERE task_id = :id
                   AND status NOT IN ('HELD','COMPLETED','CANCELLED','REJECTED')
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Resumes a collaboration-held task, restoring it to its pre-hold status.
     * Falls back to IN_PROGRESS if pre_hold_status is NULL.
     */
    public int resumeFromCollaboration(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status          = COALESCE(pre_hold_status, 'IN_PROGRESS'),
                       pre_hold_status = NULL
                 WHERE task_id = :id
                   AND status  = 'HELD'
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Marks collaboration as started and, if the task is already IN_PROGRESS,
     * also activates it immediately.
     *
     * @param taskId      the work task to update
     * @param isInProgress pass {@code true} when the task is currently IN_PROGRESS
     *                     so collaboration becomes active right away
     */
    public int markCollaborationStarted(String taskId, boolean isInProgress) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET is_collaboration_started = 1,
                       is_collaboration_active  = :active
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId)
                        .addValue("active", isInProgress));
    }

    /**
     * Activates collaboration chat + assets for a task, but only if collaboration
     * was already started. Safe to call unconditionally on status transitions.
     */
    public int activateCollaboration(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET is_collaboration_active = 1
                 WHERE task_id = :id
                   AND is_collaboration_started = 1
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Deactivates collaboration chat + assets for a task, but only if collaboration
     * was already started. Safe to call unconditionally on status transitions.
     */
    public int deactivateCollaboration(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET is_collaboration_active = 0
                 WHERE task_id = :id
                   AND is_collaboration_started = 1
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Live count of capacity-consuming tasks for a single user (excludes
     * COMPLETED, CANCELLED, and HELD). Used by the routing engine's capacity
     * report to compute available slots without trusting the denormalized
     * {@code users.current_active_tasks} counter.
     */
    public int countActiveTasksByUser(int userId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM work_tasks
                 WHERE assigned_to = :uid
                   AND status IN ('ASSIGNED','ACCEPTED','IN_PROGRESS','REWORK','MANAGER_QC_REVIEW')
                """,
                new MapSqlParameterSource("uid", userId),
                Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * All work tasks across all users — feeds the Marketing Head's overview table.
     * Ordered by last-modified time (desc).
     */
    public List<WorkTask> findAll() {
        return jdbc.query(SELECT_BASE + ORDER_BY_LAST_MODIFIED, WorkTaskRepository::map);
    }

    /** Held tasks across all users — feeds the manager's "Held Tasks" tab. */
    public List<WorkTask> findHeld() {
        return jdbc.query(
                SELECT_BASE + " WHERE wt.status = 'HELD' ORDER BY wt.task_id",
                WorkTaskRepository::map);
    }

    /**
     * Tasks for a given user that the manager could legitimately hold. Returns
     * only ASSIGNED rows — tasks the user has already started (IN_PROGRESS)
     * or pushed to QC are off-limits because pulling them mid-flight would
     * waste worker effort.
     */
    public List<WorkTask> findHoldableByUser(int userId) {
        String sql = SELECT_BASE + """
                 WHERE wt.assigned_to = :uid
                   AND wt.status      = 'ASSIGNED'
                 ORDER BY
                   CASE
                     WHEN c.priority = 'LOW'    THEN 0
                     WHEN c.priority = 'MEDIUM' THEN 1
                     WHEN c.priority = 'HIGH'   THEN 2
                     ELSE                            3
                   END,
                   COALESCE(wt.updated_at, wt.created_at) ASC
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource("uid", userId),
                WorkTaskRepository::map);
    }

    /**
     * Marks every non-terminal task on the campaign as CANCELLED. Returns the
     * list of (taskId, assignedTo) rows that were just cancelled — the caller
     * is responsible for decrementing each assignee's active-task counter.
     *
     * <p>Used whenever a campaign is closed before its work is finished:
     * QC-rejection of any one task, marketing-stage rejection of an
     * already-routed campaign, and manager intervention reject. Without this,
     * sibling tasks remain assigned & active, polluting workers' queues and
     * skewing capacity decisions.
     */
    public List<java.util.Map<String, Object>> cancelOpenTasksForCampaign(int campaignId) {
        List<java.util.Map<String, Object>> rows = jdbc.queryForList("""
                SELECT task_id, assigned_to
                  FROM work_tasks
                 WHERE campaign_id = :cId
                   AND status NOT IN ('COMPLETED','CANCELLED','REJECTED')
                """,
                new MapSqlParameterSource("cId", campaignId));
        if (!rows.isEmpty()) {
            jdbc.update("""
                    UPDATE work_tasks
                       SET status = 'CANCELLED'
                     WHERE campaign_id = :cId
                       AND status NOT IN ('COMPLETED','CANCELLED','REJECTED')
                    """,
                    new MapSqlParameterSource("cId", campaignId));
        }
        return rows;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public Optional<WorkTask> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    SELECT_BASE + " WHERE wt.task_id = :id",
                    new MapSqlParameterSource("id", id),
                    WorkTaskRepository::map));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Open tab: queue by status + priority (high→low); within each bucket, newest activity first. */
    private static final String MY_TASKS_ORDER_OPEN = """
                 ORDER BY
                   CASE wt.status
                     WHEN 'IN_PROGRESS'         THEN 0
                     WHEN 'REWORK'              THEN 1
                     WHEN 'ASSIGNED'            THEN 1
                     WHEN 'MANAGER_QC_REVIEW'   THEN 2
                     WHEN 'REQUESTOR_QC_REVIEW' THEN 2
                     WHEN 'HELD'                THEN 3
                     WHEN 'COMPLETED'           THEN 4
                     WHEN 'CANCELLED'           THEN 5
                     WHEN 'REJECTED'            THEN 5
                     ELSE                            6
                   END,
                   CASE
                     WHEN c.priority = 'HIGH' AND tt.task_name = 'Paid Ads' THEN 0
                     WHEN c.priority = 'HIGH'                                THEN 1
                     WHEN c.priority = 'MEDIUM'                              THEN 2
                     WHEN c.priority = 'LOW'                                 THEN 3
                     ELSE                                                          4
                   END,
                   CASE wt.status
                     WHEN 'REWORK'   THEN 0
                     WHEN 'ASSIGNED' THEN 1
                     ELSE                 2
                   END,
                   COALESCE(wt.updated_at, wt.created_at) DESC,
                   CAST(SUBSTRING(wt.task_id, 11) AS UNSIGNED) DESC
                """;

    /** Other tabs: most recently modified first. */
    private static final String MY_TASKS_ORDER_RECENT = """
                 ORDER BY COALESCE(wt.updated_at, wt.created_at) DESC,
                          CAST(SUBSTRING(wt.task_id, 11) AS UNSIGNED) DESC
                """;

    private static String myTasksOrderFor(String statusGroup) {
        if (statusGroup != null && "OPEN".equalsIgnoreCase(statusGroup.trim())) {
            return MY_TASKS_ORDER_OPEN;
        }
        return MY_TASKS_ORDER_RECENT;
    }

    public List<WorkTask> findByAssignedTo(int userId) {
        return findByAssignedToFiltered(userId, null);
    }

    /**
     * Same as {@link #findByAssignedTo} but additionally filters by a free-text
     * search across task ID, campaign ID, task name, task type, and requestor name.
     * When {@code search} is null or blank the method behaves identically to
     * {@link #findByAssignedTo}.
     */
    public List<WorkTask> findByAssignedToFiltered(int userId, String search) {
        MapSqlParameterSource params = new MapSqlParameterSource("uid", userId);
        String whereClause = " WHERE wt.assigned_to = :uid";
        if (search != null && !search.isBlank()) {
            whereClause += """
                      AND (wt.task_id                  LIKE :q
                        OR CAST(wt.campaign_id AS CHAR) LIKE :q
                        OR gt.task_name                LIKE :q
                        OR tt.task_name                LIKE :q
                        OR req.full_name               LIKE :q
                        OR c.store_id                  LIKE :q
                        OR c.contact_number            LIKE :q)
                    """;
            params.addValue("q", "%" + search.trim() + "%");
        }
        return jdbc.query(SELECT_BASE + whereClause + MY_TASKS_ORDER_RECENT, params, WorkTaskRepository::map);
    }

    // ── My-Tasks paged API ────────────────────────────────────────────────────

    /**
     * Appends optional search LIKE clauses and a status-group (tab) filter to
     * {@code where}.  Mutates {@code params} to add any required named values.
     */
    private static void appendSearchAndTabClauses(StringBuilder where,
                                                   String search,
                                                   String statusGroup,
                                                   MapSqlParameterSource params) {
        if (search != null && !search.isBlank()) {
            where.append("""
                      AND (wt.task_id                  LIKE :q
                        OR CAST(wt.campaign_id AS CHAR) LIKE :q
                        OR gt.task_name                LIKE :q
                        OR tt.task_name                LIKE :q
                        OR req.full_name               LIKE :q
                        OR c.store_id                  LIKE :q
                        OR c.contact_number            LIKE :q)
                    """);
            params.addValue("q", "%" + search.trim() + "%");
        }
        if (statusGroup == null) return;
        switch (statusGroup.toUpperCase()) {
            case "OPEN" ->
                where.append("""
                       AND wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK')
                       AND (c.status IS NULL OR c.status NOT IN ('REJECTED','COMPLETED'))
                    """);
            case "QC" ->
                where.append(" AND wt.status IN ('MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW')");
            case "DONE" ->
                where.append(" AND wt.status = 'COMPLETED'");
            case "HELD" ->
                where.append(" AND wt.status = 'HELD'");
            case "CANCELLED" ->
                where.append("""
                       AND (wt.status = 'CANCELLED'
                         OR (wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK',
                                           'MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW')
                             AND c.status IN ('REJECTED','COMPLETED')))
                    """);
            // "ALL" or unknown → no extra filter
        }
    }

    /** Total rows matching the given tab + optional search filter.  Used for pagination metadata. */
    public long countForMyTasks(int userId, String search, String statusGroup) {
        MapSqlParameterSource params = new MapSqlParameterSource("uid", userId);
        StringBuilder where = new StringBuilder(" WHERE wt.assigned_to = :uid");
        appendSearchAndTabClauses(where, search, statusGroup, params);
        Long count = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        return count != null ? count : 0L;
    }

    /** One page of tasks for the My-Tasks view, applying tab + search filters and priority ordering. */
    public List<WorkTask> findForMyTasksPaged(int userId, String search, String statusGroup,
                                              int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("uid", userId)
                .addValue("lim", size)
                .addValue("off", (long) page * size);
        StringBuilder where = new StringBuilder(" WHERE wt.assigned_to = :uid");
        appendSearchAndTabClauses(where, search, statusGroup, params);
        String sql = SELECT_BASE + where + myTasksOrderFor(statusGroup) + " LIMIT :lim OFFSET :off";
        return jdbc.query(sql, params, WorkTaskRepository::map);
    }

    /**
     * Counts how many of the user's tasks are currently IN_PROGRESS on a live campaign.
     * Used to calculate available queue slots for the canStart flag.
     */
    public int countInFlight(int userId) {
        String sql = """
                SELECT COUNT(*)
                  FROM work_tasks wt
                  LEFT JOIN campaigns c ON c.campaign_id = wt.campaign_id
                 WHERE wt.assigned_to = :uid
                   AND wt.status = 'IN_PROGRESS'
                   AND (c.status IS NULL OR c.status NOT IN ('REJECTED','COMPLETED'))
                """;
        Integer n = jdbc.queryForObject(sql, new MapSqlParameterSource("uid", userId), Integer.class);
        return n != null ? n : 0;
    }

    /**
     * Returns the task IDs of the first {@code slots} ASSIGNED tasks in priority order.
     * These are the tasks the user is allowed to start next.
     */
    public List<String> findStartableTaskIds(int userId, int slots) {
        if (slots <= 0) return List.of();
        String sql = "SELECT wt.task_id " + FROM_JOINS
                + " WHERE wt.assigned_to = :uid"
                + " AND wt.status = 'ASSIGNED'"
                + " AND (c.status IS NULL OR c.status NOT IN ('REJECTED','COMPLETED'))"
                + MY_TASKS_ORDER_OPEN
                + " LIMIT :slots";
        return jdbc.query(sql,
                new MapSqlParameterSource("uid", userId).addValue("slots", slots),
                (rs, row) -> rs.getString("task_id"));
    }

    /**
     * Tab badge counts across ALL of the user's tasks (no search / tab filter applied).
     * Keys: open, held, qc, done, cancelled, all.
     */
    public Map<String, Long> getTabCounts(int userId) {
        String sql = """
                SELECT
                  SUM(CASE WHEN wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK')
                            AND (c.status IS NULL OR c.status NOT IN ('REJECTED','COMPLETED'))
                       THEN 1 ELSE 0 END) AS open_count,
                  SUM(CASE WHEN wt.status = 'HELD'
                       THEN 1 ELSE 0 END) AS held_count,
                  SUM(CASE WHEN wt.status IN ('MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW')
                       THEN 1 ELSE 0 END) AS qc_count,
                  SUM(CASE WHEN wt.status = 'COMPLETED'
                       THEN 1 ELSE 0 END) AS done_count,
                  SUM(CASE WHEN wt.status = 'CANCELLED'
                            OR (wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK',
                                              'MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW')
                                AND c.status IN ('REJECTED','COMPLETED'))
                       THEN 1 ELSE 0 END) AS cancelled_count,
                  COUNT(*) AS all_count
                FROM work_tasks wt
                LEFT JOIN campaigns c ON c.campaign_id = wt.campaign_id
                WHERE wt.assigned_to = :uid
                """;
        return jdbc.queryForObject(sql, new MapSqlParameterSource("uid", userId), (rs, row) -> {
            Map<String, Long> map = new LinkedHashMap<>();
            map.put("open",      rs.getLong("open_count"));
            map.put("held",      rs.getLong("held_count"));
            map.put("qc",        rs.getLong("qc_count"));
            map.put("done",      rs.getLong("done_count"));
            map.put("cancelled", rs.getLong("cancelled_count"));
            map.put("all",       rs.getLong("all_count"));
            return map;
        });
    }

    // ── End My-Tasks paged API ────────────────────────────────────────────────

    public List<WorkTask> findByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) return java.util.Collections.emptyList();
        return jdbc.query(
                SELECT_BASE + " WHERE wt.task_id IN (:ids)",
                new MapSqlParameterSource("ids", ids),
                WorkTaskRepository::map);
    }

    public List<WorkTask> findByCampaignId(int campaignId) {
        return jdbc.query(
                SELECT_BASE + " WHERE wt.campaign_id = :cId ORDER BY wt.task_id",
                new MapSqlParameterSource("cId", campaignId),
                WorkTaskRepository::map);
    }

    /**
     * All COMPLETED work tasks belonging to campaigns requested by the given
     * requestor. Used by the requestor's "Completed Tasks" page to show every
     * approved deliverable along with its submitted assets.
     * Ordered newest-first by requestor_approved_at so the most recently approved task appears first.
     */
    public List<WorkTask> findCompletedByRequestorId(int requestorId) {
        String sql = SELECT_BASE +
                " WHERE c.requestor_id = :requestorId AND wt.status = 'COMPLETED'" +
                " AND NOT EXISTS (SELECT 1 FROM auto_created_tasks act WHERE act.created_task_id = wt.task_id)" +
                " ORDER BY COALESCE(wt.requestor_approved_at, wt.manager_approved_at) DESC";
        return jdbc.query(sql,
                new MapSqlParameterSource("requestorId", requestorId),
                WorkTaskRepository::map);
    }

    /** All REQUESTOR_QC_REVIEW tasks across all campaigns — admin/manager view. */
    public List<WorkTask> findAllRequestorQcReview() {
        String sql = SELECT_BASE +
                " WHERE wt.status = 'REQUESTOR_QC_REVIEW'" +
                " AND c.status NOT IN ('REJECTED','CANCELLED')" +
                " ORDER BY COALESCE(wt.manager_approved_at, wt.submitted_at) DESC";
        return jdbc.query(sql, WorkTaskRepository::map);
    }

    /**
     * Paged, filterable REQUESTOR_QC_REVIEW tasks.
     * Pass requestorId=-1 and isAdmin=true to get all tasks (admin view).
     */
    public PagedResponse<WorkTask> findRequestorQcReviewPaged(
            int requestorId, boolean isAdmin,
            String search, LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conds = new ArrayList<>();
        conds.add("wt.status = 'REQUESTOR_QC_REVIEW'");
        conds.add("c.status NOT IN ('REJECTED','CANCELLED')");

        if (!isAdmin) {
            conds.add("c.requestor_id = :requestorId");
            params.addValue("requestorId", requestorId);
        }

        if (hasValue(search)) {
            conds.add("(wt.task_id LIKE :search"
                    + " OR CAST(wt.campaign_id AS CHAR) LIKE :search"
                    + " OR gt.task_name LIKE :search"
                    + " OR tt.task_name LIKE :search"
                    + " OR u.full_name LIKE :search"
                    + " OR req.full_name LIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (dateFrom != null) {
            conds.add("wt.manager_approved_at >= :dateFrom");
            params.addValue("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.manager_approved_at <= :dateTo");
            params.addValue("dateTo", dateTo.atTime(23, 59, 59));
        }

        String where = " WHERE " + String.join(" AND ", conds);
        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where
                + " ORDER BY COALESCE(wt.manager_approved_at, wt.submitted_at) DESC,"
                + " CAST(SUBSTRING(wt.task_id, 11) AS UNSIGNED) DESC"
                + " LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    /**
     * All tasks in REQUESTOR_QC_REVIEW for a given requestor's campaigns.
     * Used by the requestor's QC review page.
     */
    public List<WorkTask> findRequestorQcReviewByRequestorId(int requestorId) {
        String sql = SELECT_BASE +
                " WHERE c.requestor_id = :requestorId AND wt.status = 'REQUESTOR_QC_REVIEW'" +
                " AND c.status NOT IN ('REJECTED','CANCELLED')" +
                " ORDER BY COALESCE(wt.manager_approved_at, wt.submitted_at) DESC";
        return jdbc.query(sql,
                new MapSqlParameterSource("requestorId", requestorId),
                WorkTaskRepository::map);
    }

    /** All COMPLETED work tasks across every campaign — for admin / manager views. */
    public List<WorkTask> findAllCompleted() {
        String sql = SELECT_BASE +
                " WHERE wt.status = 'COMPLETED'" +
                " ORDER BY COALESCE(wt.requestor_approved_at, wt.manager_approved_at) DESC";
        return jdbc.query(sql, WorkTaskRepository::map);
    }

    /**
     * Returns the most recently created work task for a given granular_task_id in a campaign.
     * Used after followup-task routing to locate newly created work tasks for file linking.
     */
    public Optional<WorkTask> findLatestByGranularTaskIdAndCampaignId(String granularTaskId, int campaignId) {
        List<WorkTask> rows = jdbc.query(
                SELECT_BASE + " WHERE wt.campaign_id = :cId AND wt.granular_task_id = :gId ORDER BY wt.task_id DESC LIMIT 1",
                new MapSqlParameterSource("cId", campaignId).addValue("gId", granularTaskId),
                WorkTaskRepository::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Returns the first work task matching the given granular_task_id in this campaign.
     * Used when deleting a task spec to locate and remove the linked work task.
     */
    public Optional<WorkTask> findOneByGranularTaskIdAndCampaignId(String granularTaskId, int campaignId) {
        List<WorkTask> rows = jdbc.query(
                SELECT_BASE + " WHERE wt.campaign_id = :cId AND wt.granular_task_id = :gId LIMIT 1",
                new MapSqlParameterSource("cId", campaignId).addValue("gId", granularTaskId),
                WorkTaskRepository::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int deleteByTaskId(String taskId) {
        return jdbc.update(
                "DELETE FROM work_tasks WHERE task_id = :id",
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Returns true if the campaign has at least one work task that has been started
     * (i.e. status IN_PROGRESS, MANAGER_QC_REVIEW, REQUESTOR_QC_REVIEW, REWORK, or COMPLETED).
     * Used to guard against deleting a campaign whose work has already begun.
     */
    public boolean hasStartedTasksForCampaign(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks " +
                "WHERE campaign_id = :cId " +
                "  AND status IN ('IN_PROGRESS','MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW','REWORK','COMPLETED')",
                new MapSqlParameterSource("cId", campaignId),
                Integer.class);
        return count != null && count > 0;
    }

    public List<WorkTask> findPendingQcReview() {
        // Exclude tasks whose parent campaign is already terminal.
        return jdbc.query(
                SELECT_BASE + " WHERE wt.status = 'MANAGER_QC_REVIEW' " +
                "  AND c.status NOT IN ('REJECTED','COMPLETED') " +
                "ORDER BY wt.task_id",
                WorkTaskRepository::map);
    }

    /**
     * Paged + filtered QC review queue.
     * Excludes tasks assigned to {@code excludeUserId} (reviewer cannot self-approve).
     * When {@code allowedWorkerRoleIds} is non-empty, only tasks whose assignee holds
     * one of those roles are included (QC routing filter).  An empty list means
     * "show all" — the backwards-compatible default when no routing is configured.
     * Sorts newest-submitted first.
     */
    public PagedResponse<WorkTask> findPendingQcReviewPaged(
            int excludeUserId,
            java.util.List<String> allowedWorkerRoleIds,
            String search, LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("excludeUserId", excludeUserId);
        List<String> conds = new ArrayList<>();
        conds.add("wt.status = 'MANAGER_QC_REVIEW'");
        conds.add("c.status NOT IN ('REJECTED','COMPLETED')");
        conds.add("wt.assigned_to != :excludeUserId");

        if (allowedWorkerRoleIds != null && !allowedWorkerRoleIds.isEmpty()) {
            conds.add("wt.assigned_to IN ("
                    + "SELECT ur.user_id FROM user_roles ur "
                    + "WHERE ur.role_id IN (:allowedWorkerRoleIds))");
            params.addValue("allowedWorkerRoleIds", allowedWorkerRoleIds);
        }

        if (hasValue(search)) {
            conds.add("(wt.task_id LIKE :search"
                    + " OR CAST(wt.campaign_id AS CHAR) LIKE :search"
                    + " OR gt.task_name LIKE :search"
                    + " OR tt.task_name LIKE :search"
                    + " OR u.full_name LIKE :search"
                    + " OR req.full_name LIKE :search)");
            params.addValue("search", "%" + search.trim() + "%");
        }
        if (dateFrom != null) {
            conds.add("wt.submitted_at >= :dateFrom");
            params.addValue("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.submitted_at <= :dateTo");
            params.addValue("dateTo", dateTo.atTime(23, 59, 59));
        }

        String where = " WHERE " + String.join(" AND ", conds);
        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where
                + " ORDER BY wt.submitted_at DESC, CAST(SUBSTRING(wt.task_id, 11) AS UNSIGNED) DESC"
                + " LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    /**
     * Counts tasks for a campaign that are still open (i.e. neither COMPLETED
     * nor CANCELLED). Used to detect when a campaign is fully done — the
     * campaign should flip to COMPLETED only when every remaining task has
     * been QC-approved. CANCELLED tasks (from a sibling rejection or a
     * campaign-level reject) must not block completion of an otherwise
     * approvable workflow.
     */
    /**
     * Counts tasks still waiting for work to be done (not yet in a terminal state or
     * waiting for requestor QC sign-off). Used to decide whether the campaign can
     * advance to REQUESTOR_QC_REVIEW once the manager finishes their last approval.
     */
    public int countIncomplete(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks " +
                " WHERE campaign_id = :cId AND status NOT IN ('COMPLETED','CANCELLED','REJECTED','REQUESTOR_QC_REVIEW')",
                new MapSqlParameterSource("cId", campaignId),
                Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Counts tasks still needing final requestor approval (REQUESTOR_QC_REVIEW).
     * Returns 0 when all tasks are done (COMPLETED / CANCELLED / REJECTED).
     */
    public int countPendingRequestorApproval(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks " +
                " WHERE campaign_id = :cId AND status NOT IN ('COMPLETED','CANCELLED','REJECTED')",
                new MapSqlParameterSource("cId", campaignId),
                Integer.class);
        return count == null ? 0 : count;
    }

    /** Returns how many COMPLETED tasks exist for this campaign. */
    public int countCompleted(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks WHERE campaign_id = :cId AND status = 'COMPLETED'",
                new MapSqlParameterSource("cId", campaignId),
                Integer.class);
        return count == null ? 0 : count;
    }

    // -------------------------------------------------------------------------
    // Time-tracking reports (Module 3 — efficiency reports)
    // -------------------------------------------------------------------------

    /**
     * Per-user time-tracking summary between [from, to] (inclusive on completed_at).
     * Returns one row per user.
     */
    public List<java.util.Map<String, Object>> timeSummary(java.time.LocalDate from,
                                                            java.time.LocalDate to) {
        String fromCutoff = from == null ? null : from + " 00:00:00";
        String toCutoff   = to   == null ? null : to   + " 23:59:59";
        // NOTE: We deliberately derive `current_active_tasks` from the actual
        // work_tasks rows rather than reading users.current_active_tasks. The
        // denormalized counter is best-effort (kept in sync by the routing
        // engine + QC approvals) and can drift on edge paths — manual reroute,
        // intervention reject, deleted campaign, etc. The work_tasks table is
        // the source of truth, so the report stays correct regardless.
        return jdbc.queryForList("""
                SELECT u.user_id,
                       u.full_name,
                       r.role_name,
                       SUM(CASE WHEN wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK','MANAGER_QC_REVIEW')
                                THEN 1 ELSE 0 END)                              AS current_active_tasks,
                       SUM(CASE WHEN wt.status NOT IN ('CANCELLED','REJECTED') THEN 1 ELSE 0 END) AS total_tasks,
                       SUM(CASE WHEN wt.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_tasks,
                       SUM(CASE WHEN wt.status IN ('IN_PROGRESS','REWORK') THEN 1 ELSE 0 END) AS in_flight_tasks,
                       SUM(CASE WHEN wt.status IN ('CANCELLED','REJECTED') THEN 1 ELSE 0 END) AS cancelled_tasks,
                       COALESCE(SUM(CASE WHEN wt.status = 'COMPLETED'
                                          AND (:from IS NULL OR wt.requestor_approved_at >= :from)
                                          AND (:to   IS NULL OR wt.requestor_approved_at <= :to)
                                         THEN wt.total_time_logged_minutes ELSE 0 END), 0) AS minutes_logged
                FROM users u
                LEFT JOIN user_roles ur ON ur.user_id = u.user_id
                    AND ur.role_id = (SELECT MIN(ur2.role_id) FROM user_roles ur2 WHERE ur2.user_id = u.user_id)
                LEFT JOIN roles      r  ON r.role_id  = ur.role_id
                LEFT JOIN work_tasks wt ON wt.assigned_to = u.user_id
                WHERE u.status = 'ACTIVE'
                GROUP BY u.user_id, u.full_name, r.role_name
                ORDER BY minutes_logged DESC, u.full_name
                """,
                new MapSqlParameterSource("from", fromCutoff).addValue("to", toCutoff));
    }

    // -------------------------------------------------------------------------
    // Analytics summary — Reports dashboard
    // -------------------------------------------------------------------------

    /**
     * Returns a single aggregated snapshot used by the manager Reports page:
     * campaign counts by status & priority, task counts by status, 8-week
     * completion trend, team performance, and top rework offenders.
     */
    public java.util.Map<String, Object> analyticsSummary() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();

        // ── Campaign counts by status ─────────────────────────────────────────
        var campaignByStatus = jdbc.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM campaigns GROUP BY status",
                new MapSqlParameterSource());
        result.put("campaignsByStatus", campaignByStatus);

        // ── Campaign counts by priority ───────────────────────────────────────
        var campaignByPriority = jdbc.queryForList(
                "SELECT priority, COUNT(*) AS cnt FROM campaigns GROUP BY priority ORDER BY FIELD(priority,'HIGH','MEDIUM','LOW')",
                new MapSqlParameterSource());
        result.put("campaignsByPriority", campaignByPriority);

        // ── Tasks by task type (top 8) ────────────────────────────────────────
        var campaignByType = jdbc.queryForList(
                """
                SELECT COALESCE(tt.task_name, 'Unknown') AS name,
                       COUNT(*) AS cnt
                FROM work_tasks wt
                LEFT JOIN granular_tasks gt ON gt.task_id = wt.granular_task_id
                LEFT JOIN task_types tt ON tt.task_type_id = gt.task_type_id
                GROUP BY tt.task_type_id, tt.task_name
                ORDER BY cnt DESC LIMIT 8
                """,
                new MapSqlParameterSource());
        result.put("campaignsByType", campaignByType);

        // ── Task counts by status ─────────────────────────────────────────────
        var taskByStatus = jdbc.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM work_tasks GROUP BY status",
                new MapSqlParameterSource());
        result.put("tasksByStatus", taskByStatus);

        // ── Weekly completed tasks — all time ────────────────────────────────
        var weeklyCompleted = jdbc.queryForList(
                """
                SELECT DATE_FORMAT(
                         COALESCE(requestor_approved_at, manager_approved_at, updated_at, created_at),
                         '%Y-W%u') AS week,
                       COUNT(*) AS cnt
                FROM work_tasks
                WHERE status = 'COMPLETED'
                GROUP BY week ORDER BY week
                """,
                new MapSqlParameterSource());
        result.put("weeklyCompleted", weeklyCompleted);

        // ── Weekly new campaigns — last 10 weeks ──────────────────────────────
        var weeklyNew = jdbc.queryForList(
                """
                SELECT DATE_FORMAT(created_at,'%Y-W%u') AS week,
                       COUNT(*) AS cnt
                FROM campaigns
                WHERE created_at >= DATE_SUB(NOW(), INTERVAL 10 WEEK)
                GROUP BY week ORDER BY week
                """,
                new MapSqlParameterSource());
        result.put("weeklyNew", weeklyNew);

        // ── Team performance ──────────────────────────────────────────────────
        var team = jdbc.queryForList(
                """
                SELECT u.full_name                                              AS name,
                       SUM(CASE WHEN wt.status NOT IN ('CANCELLED','REJECTED') THEN 1 ELSE 0 END) AS total,
                       SUM(CASE WHEN wt.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                       SUM(CASE WHEN wt.status IN ('IN_PROGRESS','REWORK','MANAGER_QC_REVIEW','ASSIGNED') THEN 1 ELSE 0 END) AS active,
                       COALESCE(SUM(wt.total_time_logged_minutes),0)            AS minutesLogged,
                       COALESCE(SUM(
                           (SELECT COUNT(*) FROM approvals_log al
                            WHERE al.task_id = wt.task_id
                              AND al.action_taken = 'NEEDS_REWORK')
                       ),0)                                                     AS reworkCount
                FROM users u
                LEFT JOIN work_tasks wt ON wt.assigned_to = u.user_id
                WHERE u.status = 'ACTIVE'
                GROUP BY u.user_id, u.full_name
                HAVING total > 0
                ORDER BY completed DESC, total DESC
                """,
                new MapSqlParameterSource());
        result.put("team", team);

        // ── Top reworked tasks ────────────────────────────────────────────────
        var topRework = jdbc.queryForList(
                """
                SELECT wt.task_id, gt.task_name, u.full_name AS assignee,
                       COUNT(al.log_id) AS reworkCount,
                       MAX(al.created_at) AS lastRework
                FROM work_tasks wt
                JOIN approvals_log al ON al.task_id = wt.task_id
                                     AND al.action_taken = 'NEEDS_REWORK'
                LEFT JOIN granular_tasks gt ON gt.task_id = wt.granular_task_id
                LEFT JOIN users u ON u.user_id = wt.assigned_to
                GROUP BY wt.task_id, gt.task_name, u.full_name
                ORDER BY reworkCount DESC LIMIT 8
                """,
                new MapSqlParameterSource());
        result.put("topRework", topRework);

        // ── Totals ────────────────────────────────────────────────────────────
        Integer totalCampaigns  = jdbc.queryForObject("SELECT COUNT(*) FROM campaigns",    new MapSqlParameterSource(), Integer.class);
        Integer totalTasks      = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status NOT IN ('CANCELLED','REJECTED')", new MapSqlParameterSource(), Integer.class);
        Integer completedTasks  = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status = 'COMPLETED'",  new MapSqlParameterSource(), Integer.class);
        Integer pendingQc       = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status = 'MANAGER_QC_REVIEW'",  new MapSqlParameterSource(), Integer.class);
        Integer inRework        = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status = 'REWORK'",     new MapSqlParameterSource(), Integer.class);
        Double  avgMinutes      = jdbc.queryForObject(
                "SELECT AVG(total_time_logged_minutes) FROM work_tasks WHERE status='COMPLETED' AND total_time_logged_minutes > 0",
                new MapSqlParameterSource(), Double.class);

        result.put("totals", java.util.Map.of(
                "campaigns",     totalCampaigns  == null ? 0 : totalCampaigns,
                "tasks",         totalTasks      == null ? 0 : totalTasks,
                "completed",     completedTasks  == null ? 0 : completedTasks,
                "pendingQc",     pendingQc       == null ? 0 : pendingQc,
                "inRework",      inRework        == null ? 0 : inRework,
                "avgMinutes",    avgMinutes      == null ? 0.0 : Math.round(avgMinutes * 10.0) / 10.0
        ));

        return result;
    }

    // -------------------------------------------------------------------------
    // Paged / filtered queries
    // -------------------------------------------------------------------------

    /**
     * Paginated + filtered overview of ALL work tasks (Marketing Head / manager).
     * All filter params are optional; pass null/blank to skip a filter.
     * {@code dateFrom}/{@code dateTo} are matched against {@code wt.assigned_at}.
     */
    public PagedResponse<WorkTask> findAllPaged(
            String taskId, String campaignId,
            String requestorName, String assigneeName,
            String taskType, String priority, String status,
            Boolean autoGeneratedOnly,
            LocalDate dateFrom, LocalDate dateTo,
            String actionDoneBy,
            String storeId,
            String sourceTaskId,
            String contentRequestedBy,
            String contentRequestStatus,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildAllTasksWhere(params, taskId, campaignId,
                requestorName, assigneeName, taskType, priority, status, autoGeneratedOnly, dateFrom, dateTo,
                actionDoneBy, storeId, sourceTaskId, contentRequestedBy, contentRequestStatus);

        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where + ORDER_BY_LAST_MODIFIED + " LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    /**
     * Paginated + filtered COMPLETED tasks for a specific requestor.
     * {@code dateFrom}/{@code dateTo} are matched against {@code wt.requestor_approved_at}.
     */
    public PagedResponse<WorkTask> findCompletedByRequestorIdPaged(
            int requestorId,
            String campaignId, String taskId, String taskName,
            String taskType, String completedBy,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("requestorId", requestorId);
        List<String> conds = new ArrayList<>();
        conds.add("c.requestor_id = :requestorId");
        conds.add("wt.status = 'COMPLETED'");
        conds.add("NOT EXISTS (SELECT 1 FROM auto_created_tasks act WHERE act.created_task_id = wt.task_id)");

        if (hasValue(campaignId)) {
            conds.add("CAST(wt.campaign_id AS CHAR) LIKE :campaignId");
            params.addValue("campaignId", "%" + campaignId.trim() + "%");
        }
        if (hasValue(taskId)) {
            conds.add("wt.task_id LIKE :taskId");
            params.addValue("taskId", "%" + taskId.trim() + "%");
        }
        if (hasValue(taskName)) {
            conds.add("COALESCE(gt.task_name, tt.task_name) = :taskName");
            params.addValue("taskName", taskName.trim());
        }
        if (hasValue(taskType)) {
            conds.add("tt.task_name = :taskType");
            params.addValue("taskType", taskType.trim());
        }
        if (hasValue(completedBy)) {
            conds.add("u.full_name LIKE :completedBy");
            params.addValue("completedBy", "%" + completedBy.trim() + "%");
        }
        if (dateFrom != null) {
            conds.add("wt.requestor_approved_at >= :completedFrom");
            params.addValue("completedFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.requestor_approved_at <= :completedTo");
            params.addValue("completedTo", dateTo.atTime(23, 59, 59));
        }

        String where = " WHERE " + String.join(" AND ", conds);

        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where + " ORDER BY COALESCE(wt.requestor_approved_at, wt.manager_approved_at) DESC LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    // ─── WHERE clause builder for AllTasks ───────────────────────────────────

    private static String buildAllTasksWhere(
            MapSqlParameterSource params,
            String taskId, String campaignId,
            String requestorName, String assigneeName,
            String taskType, String priority, String status, Boolean autoGeneratedOnly,
            LocalDate dateFrom, LocalDate dateTo, String actionDoneBy,
            String storeId,
            String sourceTaskId, String contentRequestedBy, String contentRequestStatus) {

        List<String> conds = new ArrayList<>();

        if (hasValue(taskId)) {
            conds.add("wt.task_id LIKE :taskId");
            params.addValue("taskId", "%" + taskId.trim() + "%");
        }
        if (hasValue(campaignId)) {
            conds.add("CAST(wt.campaign_id AS CHAR) LIKE :campaignId");
            params.addValue("campaignId", "%" + campaignId.trim() + "%");
        }
        if (hasValue(requestorName)) {
            conds.add("req.full_name LIKE :requestorName");
            params.addValue("requestorName", "%" + requestorName.trim() + "%");
        }
        if (hasValue(assigneeName)) {
            conds.add("u.full_name LIKE :assigneeName");
            params.addValue("assigneeName", "%" + assigneeName.trim() + "%");
        }
        if (hasValue(taskType)) {
            conds.add("COALESCE(gt.task_name, tt.task_name) = :taskType");
            params.addValue("taskType", taskType.trim());
        }
        if (hasValue(priority)) {
            conds.add("c.priority = :priority");
            params.addValue("priority", priority.trim());
        }
        if (hasValue(status)) {
            conds.add("wt.status = :status");
            params.addValue("status", status.trim());
        }
        if (autoGeneratedOnly != null) {
            conds.add(autoGeneratedOnly
                    ? "EXISTS (SELECT 1 FROM auto_created_tasks act WHERE act.created_task_id = wt.task_id)"
                    : "NOT EXISTS (SELECT 1 FROM auto_created_tasks act WHERE act.created_task_id = wt.task_id)");
        }
        if (dateFrom != null) {
            conds.add("wt.created_at >= :dateFrom");
            params.addValue("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.created_at <= :dateTo");
            params.addValue("dateTo", dateTo.atTime(23, 59, 59));
        }
        if (hasValue(actionDoneBy)) {
            conds.add("""
                    (SELECT u_adb.full_name FROM approvals_log al_adb
                      LEFT JOIN users u_adb ON u_adb.user_id = al_adb.reviewer_id
                      WHERE al_adb.task_id = wt.task_id
                      ORDER BY al_adb.log_id DESC LIMIT 1) LIKE :actionDoneBy
                    """);
            params.addValue("actionDoneBy", "%" + actionDoneBy.trim() + "%");
        }
        if (hasValue(storeId)) {
            conds.add("c.store_id LIKE :storeId");
            params.addValue("storeId", "%" + storeId.trim() + "%");
        }
        if (hasValue(sourceTaskId)) {
            conds.add("""
                    EXISTS (SELECT 1 FROM auto_created_tasks act
                            WHERE act.created_task_id = wt.task_id
                              AND act.source_task_id LIKE :sourceTaskId)
                    """);
            params.addValue("sourceTaskId", "%" + sourceTaskId.trim() + "%");
        }
        if (hasValue(contentRequestedBy)) {
            conds.add("""
                    EXISTS (SELECT 1 FROM auto_created_tasks act
                            INNER JOIN users cr ON cr.user_id = act.requested_by_user_id
                            WHERE act.created_task_id = wt.task_id
                              AND cr.full_name LIKE :contentRequestedBy)
                    """);
            params.addValue("contentRequestedBy", "%" + contentRequestedBy.trim() + "%");
        }
        if (hasValue(contentRequestStatus)) {
            conds.add("""
                    EXISTS (SELECT 1 FROM auto_created_tasks act
                            WHERE act.created_task_id = wt.task_id
                              AND act.status = :contentRequestStatus)
                    """);
            params.addValue("contentRequestStatus", contentRequestStatus.trim());
        }

        return conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
    }

    private static boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Queries the highest numeric suffix of existing WORK-TASK-X IDs and
     * returns the next one. Synchronized to prevent duplicates under concurrent
     * request bursts; the UNIQUE PRIMARY KEY in the DB is the final safety net.
     */
    private synchronized String generateNextTaskId() {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(task_id, 11) AS UNSIGNED)) " +
                "FROM work_tasks WHERE task_id LIKE 'WORK-TASK-%'",
                new MapSqlParameterSource(),
                Integer.class);
        int next = (max == null) ? 1 : max + 1;
        return "WORK-TASK-" + next;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WorkTask map(ResultSet rs, int rowNum) throws SQLException {
        return WorkTask.builder()
                .taskId(rs.getString("task_id"))
                .campaignId(rs.getInt("campaign_id"))
                .assignedTo(getNullableInt(rs, "assigned_to"))
                .assigneeName(rs.getString("assignee_name"))
                .granularTaskId(rs.getString("granular_task_id"))
                .granularTaskName(rs.getString("granular_task_name"))
                .taskTypeName(rs.getString("task_type_name"))
                .status(safeEnum(TaskStatus.class, rs.getString("status")))
                .collaborationStarted(rs.getBoolean("is_collaboration_started"))
                .collaborationActive(rs.getBoolean("is_collaboration_active"))
                .assignedAt(toLocalDateTime(rs, "assigned_at"))
                .acceptedAt(toLocalDateTime(rs, "accepted_at"))
                .startedAt(toLocalDateTime(rs, "started_at"))
                .submittedAt(toLocalDateTime(rs, "submitted_at"))
                .managerApprovedAt(toLocalDateTime(rs, "manager_approved_at"))
                .requestorApprovedAt(toLocalDateTime(rs, "requestor_approved_at"))
                .totalTimeLoggedMinutes(getNullableInt(rs, "total_time_logged_minutes"))
                .dynamicDeadline(toLocalDateTime(rs, "dynamic_deadline"))
                .submissionNotes(rs.getString("submission_notes"))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .updatedAt(toLocalDateTime(rs, "updated_at"))
                .reworkCount(getNullableInt(rs, "rework_count"))
                .requestorReworkCount(getNullableInt(rs, "requestor_rework_count"))
                .latestManagerReworkComment(rs.getString("latest_manager_rework_comment"))
                .latestRequestorReworkComment(rs.getString("latest_requestor_rework_comment"))
                .latestReworkComment(rs.getString("latest_rework_comment"))
                .latestReworkSource(rs.getString("latest_rework_source"))
                .latestActionDoneByName(rs.getString("latest_action_done_by_name"))
                .campaignDeadline(rs.getDate("campaign_deadline") == null ? null : rs.getDate("campaign_deadline").toLocalDate())
                .campaignPriority(safeEnum(Priority.class, rs.getString("campaign_priority")))
                .campaignStatus(safeEnum(CampaignStatus.class, rs.getString("campaign_status")))
                .requestorId(getNullableInt(rs, "requestor_id"))
                .requestorName(rs.getString("requestor_name"))
                .storeId(rs.getString("campaign_store_id"))
                .contactNumber(rs.getString("campaign_contact_number"))
                .hasActiveComments(rs.getBoolean("has_active_comments"))
                .build();
    }

    private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> clazz, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Enum.valueOf(clazz, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
