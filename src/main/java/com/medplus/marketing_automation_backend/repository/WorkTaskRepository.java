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
import java.util.List;
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

    private static final String SELECT_BASE = """
            SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                   wt.granular_task_id,
                   wt.status,
                   wt.is_collaboration_started,
                   wt.is_collaboration_active,
                   wt.assigned_at, wt.accepted_at, wt.started_at,
                   wt.submitted_at, wt.completed_at,
                   wt.total_time_logged_minutes, wt.dynamic_deadline,
                   wt.submission_notes,
                   wt.created_at, wt.updated_at,
                   u.full_name    AS assignee_name,
                   gt.task_name  AS granular_task_name,
                   tt.task_name  AS task_type_name,
                   c.deadline    AS campaign_deadline,
                   c.priority    AS campaign_priority,
                   c.status      AS campaign_status,
                   c.requestor_id,
                   req.full_name AS requestor_name,
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
                   (SELECT u_adb.full_name FROM approvals_log al_adb
                     LEFT JOIN users u_adb ON u_adb.user_id = al_adb.reviewer_id
                     WHERE al_adb.task_id = wt.task_id
                     ORDER BY al_adb.log_id DESC LIMIT 1) AS latest_action_done_by_name
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
     * Marks a task as QC_REVIEW and logs time spent + creator's submission.
     * Stores the submission timestamp in `submitted_at` (not `completed_at` —
     * `completed_at` is reserved for the QC manager's approval timestamp).
     */
    public int complete(String taskId, int userId, LocalDateTime submittedAt, int minutesSpent,
                        String submissionNotes) {
        return jdbc.update("""
                UPDATE work_tasks
                SET status = 'QC_REVIEW',
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

    /** Marks the task as fully COMPLETED (QC manager approved). Stamps completed_at = now. */
    public int markCompleted(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status = 'COMPLETED',
                       completed_at = :now
                 WHERE task_id = :id
                """,
                new MapSqlParameterSource("id", taskId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Marks the task as CANCELLED — used when a QC reviewer rejects this task
     * outright, or when the parent campaign is rejected/intervention-rejected
     * before this task could be finished. Unlike {@link #markCompleted}, this
     * does NOT stamp {@code completed_at}, because no asset was approved.
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
     * Sends the task back for rework. Clears both {@code submitted_at} and
     * {@code completed_at} so the lifecycle timeline accurately reflects the
     * current cycle — the worker has not yet submitted this round, and the
     * task was never QC-approved. The historical submission timestamps live
     * in {@code approval_logs}, which is the right place to inspect prior
     * QC attempts.
     */
    public int markRework(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status       = 'REWORK',
                       submitted_at = NULL,
                       completed_at = NULL
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
                   SET status = 'HELD'
                 WHERE task_id = :id
                   AND status  = 'ASSIGNED'
                """,
                new MapSqlParameterSource("id", taskId));
    }

    /**
     * Re-assigns a HELD task to a (potentially different) user and flips its
     * status back to ASSIGNED. Called from the routing engine after a
     * successful capacity match during unhold. Stamps a new {@code assigned_at}
     * so time-tracking shows the latest hand-off, and clears any stale
     * acceptance/start timestamps from the previous assignment cycle.
     */
    public int reassignFromHeld(String taskId, int newAssigneeId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status      = 'ASSIGNED',
                       assigned_to = :uid,
                       assigned_at = :now,
                       accepted_at = NULL,
                       started_at  = NULL
                 WHERE task_id = :id
                   AND status  = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", newAssigneeId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
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
                   SET status          = COALESCE(pre_hold_status, 'ASSIGNED'),
                       pre_hold_status = NULL,
                       assigned_at     = :now
                 WHERE task_id     = :id
                   AND assigned_to = :uid
                   AND status      = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", userId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Resumes a HELD task by task ID only — called when the requestor saves
     * updated answers, implicitly acknowledging the worker's comment.
     * No userId guard (caller is the requestor, not the worker).
     */
    public int clearHoldByTaskId(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET status          = COALESCE(pre_hold_status, 'ASSIGNED'),
                       pre_hold_status = NULL,
                       assigned_at     = :now
                 WHERE task_id = :id
                   AND status  = 'HELD'
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Holds a task for collaboration — works from any active status
     * (ASSIGNED, ACCEPTED, IN_PROGRESS, REWORK, QC_REVIEW).
     * Saves the current status in pre_hold_status so it can be restored on resume.
     */
    public int holdForCollaboration(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET pre_hold_status = status,
                       status          = 'HELD'
                 WHERE task_id = :id
                   AND status NOT IN ('HELD','COMPLETED','CANCELLED')
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
                   AND status IN ('ASSIGNED','ACCEPTED','IN_PROGRESS','REWORK','QC_REVIEW')
                """,
                new MapSqlParameterSource("uid", userId),
                Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * All work tasks across all users — feeds the Marketing Head's overview table.
     * Ordered by campaign priority (desc) then task creation time (asc).
     */
    public List<WorkTask> findAll() {
        String sql = SELECT_BASE + """
                 ORDER BY
                   CASE c.priority
                     WHEN 'HIGH'   THEN 0
                     WHEN 'MEDIUM' THEN 1
                     WHEN 'LOW'    THEN 2
                     ELSE               3
                   END,
                   wt.created_at ASC
                """;
        return jdbc.query(sql, WorkTaskRepository::map);
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
                   wt.created_at ASC
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
                   AND status NOT IN ('COMPLETED','CANCELLED')
                """,
                new MapSqlParameterSource("cId", campaignId));
        if (!rows.isEmpty()) {
            jdbc.update("""
                    UPDATE work_tasks
                       SET status = 'CANCELLED'
                     WHERE campaign_id = :cId
                       AND status NOT IN ('COMPLETED','CANCELLED')
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

    /**
     * Lists every active task assigned to a user, sorted to enforce the
     * Module 2-C "Top Queueing" rule + the single-active-task queue UX:
     *
     *   1. The in-flight task (IN_PROGRESS) is pinned to the top, so the
     *      live timer is the first thing the worker sees when they open
     *      "My Tasks".
     *   2. Then open work in priority order: HIGH+Paid Ads → HIGH → MEDIUM
     *      → LOW. Within a priority bucket, REWORK comes before fresh
     *      ASSIGNED items because rework is more time-sensitive.
     *   3. Then QC_REVIEW (waiting on the manager).
     *   4. Then terminal states (COMPLETED / CANCELLED) at the bottom.
     *   5. Final tie-break is created_at ASC so FIFO holds inside each tier.
     */
    public List<WorkTask> findByAssignedTo(int userId) {
        // HELD tasks are now included so the worker can see them in their
        // "On Hold" bucket. They are sorted after active work so they don't
        // clutter the top of the queue.
        String sql = SELECT_BASE + """
                 WHERE wt.assigned_to = :uid
                 ORDER BY
                   CASE wt.status
                     WHEN 'IN_PROGRESS' THEN 0
                     WHEN 'REWORK'      THEN 1
                     WHEN 'ASSIGNED'    THEN 1
                     WHEN 'QC_REVIEW'   THEN 2
                     WHEN 'HELD'        THEN 3
                     WHEN 'COMPLETED'   THEN 4
                     WHEN 'CANCELLED'   THEN 5
                     ELSE                    6
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
                   wt.created_at ASC
                """;
        return jdbc.query(sql,
                new MapSqlParameterSource("uid", userId),
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
     * Ordered newest-first by completed_at so the most recently approved task
     * appears at the top.
     */
    public List<WorkTask> findCompletedByRequestorId(int requestorId) {
        String sql = SELECT_BASE +
                " WHERE c.requestor_id = :requestorId AND wt.status = 'COMPLETED'" +
                " ORDER BY wt.completed_at DESC";
        return jdbc.query(sql,
                new MapSqlParameterSource("requestorId", requestorId),
                WorkTaskRepository::map);
    }

    /** All COMPLETED work tasks across every campaign — for admin / manager views. */
    public List<WorkTask> findAllCompleted() {
        String sql = SELECT_BASE +
                " WHERE wt.status = 'COMPLETED'" +
                " ORDER BY wt.completed_at DESC";
        return jdbc.query(sql, WorkTaskRepository::map);
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
     * (i.e. status IN_PROGRESS, QC_REVIEW, REWORK, or COMPLETED).
     * Used to guard against deleting a campaign whose work has already begun.
     */
    public boolean hasStartedTasksForCampaign(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks " +
                "WHERE campaign_id = :cId " +
                "  AND status IN ('IN_PROGRESS','QC_REVIEW','REWORK','COMPLETED')",
                new MapSqlParameterSource("cId", campaignId),
                Integer.class);
        return count != null && count > 0;
    }

    public List<WorkTask> findPendingQcReview() {
        // Exclude tasks whose parent campaign is already terminal — they
        // shouldn't show up in the QC reviewer's queue. In normal flow this
        // can't happen (campaign rejection cancels its tasks), but a stale
        // tab or a partially-applied transaction could leave a QC_REVIEW row
        // pointing at a REJECTED/COMPLETED campaign.
        return jdbc.query(
                SELECT_BASE + " WHERE wt.status = 'QC_REVIEW' " +
                "  AND c.status NOT IN ('REJECTED','COMPLETED') " +
                "ORDER BY wt.task_id",
                WorkTaskRepository::map);
    }

    /**
     * Counts tasks for a campaign that are still open (i.e. neither COMPLETED
     * nor CANCELLED). Used to detect when a campaign is fully done — the
     * campaign should flip to COMPLETED only when every remaining task has
     * been QC-approved. CANCELLED tasks (from a sibling rejection or a
     * campaign-level reject) must not block completion of an otherwise
     * approvable workflow.
     */
    public int countIncomplete(int campaignId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_tasks " +
                " WHERE campaign_id = :cId AND status NOT IN ('COMPLETED','CANCELLED')",
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
                       SUM(CASE WHEN wt.status IN ('ASSIGNED','IN_PROGRESS','REWORK','QC_REVIEW')
                                THEN 1 ELSE 0 END)                              AS current_active_tasks,
                       SUM(CASE WHEN wt.status <> 'CANCELLED' THEN 1 ELSE 0 END) AS total_tasks,
                       SUM(CASE WHEN wt.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed_tasks,
                       SUM(CASE WHEN wt.status IN ('IN_PROGRESS','REWORK') THEN 1 ELSE 0 END) AS in_flight_tasks,
                       SUM(CASE WHEN wt.status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled_tasks,
                       COALESCE(SUM(CASE WHEN wt.status = 'COMPLETED'
                                          AND (:from IS NULL OR wt.completed_at >= :from)
                                          AND (:to   IS NULL OR wt.completed_at <= :to)
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

        // ── Campaigns by task type (top 8) ────────────────────────────────────
        var campaignByType = jdbc.queryForList(
                """
                SELECT COALESCE(tt.task_name, c.task_type_id, 'Unknown') AS name,
                       COUNT(c.campaign_id) AS cnt
                FROM campaigns c
                LEFT JOIN task_types tt ON JSON_CONTAINS(c.task_type_id, JSON_QUOTE(tt.task_type_id))
                GROUP BY name ORDER BY cnt DESC LIMIT 8
                """,
                new MapSqlParameterSource());
        result.put("campaignsByType", campaignByType);

        // ── Task counts by status ─────────────────────────────────────────────
        var taskByStatus = jdbc.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM work_tasks GROUP BY status",
                new MapSqlParameterSource());
        result.put("tasksByStatus", taskByStatus);

        // ── Weekly completed tasks — last 10 weeks ────────────────────────────
        var weeklyCompleted = jdbc.queryForList(
                """
                SELECT DATE_FORMAT(completed_at,'%Y-W%u') AS week,
                       COUNT(*) AS cnt
                FROM work_tasks
                WHERE status = 'COMPLETED'
                  AND completed_at >= DATE_SUB(NOW(), INTERVAL 10 WEEK)
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
                       COUNT(wt.task_id)                                        AS total,
                       SUM(CASE WHEN wt.status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed,
                       SUM(CASE WHEN wt.status IN ('IN_PROGRESS','REWORK','QC_REVIEW','ASSIGNED') THEN 1 ELSE 0 END) AS active,
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
        Integer totalTasks      = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status <> 'CANCELLED'", new MapSqlParameterSource(), Integer.class);
        Integer completedTasks  = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status = 'COMPLETED'",  new MapSqlParameterSource(), Integer.class);
        Integer pendingQc       = jdbc.queryForObject("SELECT COUNT(*) FROM work_tasks WHERE status = 'QC_REVIEW'",  new MapSqlParameterSource(), Integer.class);
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
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildAllTasksWhere(params, taskId, campaignId,
                requestorName, assigneeName, taskType, priority, status, dateFrom, dateTo);

        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        String orderBy = """
                 ORDER BY
                   CASE c.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 WHEN 'LOW' THEN 2 ELSE 3 END,
                   wt.created_at ASC
                """;
        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where + orderBy + " LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    /**
     * Paginated + filtered COMPLETED tasks for a specific requestor.
     * {@code dateFrom}/{@code dateTo} are matched against {@code wt.completed_at}.
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
            conds.add("wt.completed_at >= :completedFrom");
            params.addValue("completedFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.completed_at <= :completedTo");
            params.addValue("completedTo", dateTo.atTime(23, 59, 59));
        }

        String where = " WHERE " + String.join(" AND ", conds);

        Long total = jdbc.queryForObject(COUNT_BASE + where, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<WorkTask> content = jdbc.query(
                SELECT_BASE + where + " ORDER BY wt.completed_at DESC LIMIT :_size OFFSET :_offset",
                params, WorkTaskRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    // ─── WHERE clause builder for AllTasks ───────────────────────────────────

    private static String buildAllTasksWhere(
            MapSqlParameterSource params,
            String taskId, String campaignId,
            String requestorName, String assigneeName,
            String taskType, String priority, String status,
            LocalDate dateFrom, LocalDate dateTo) {

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
        if (dateFrom != null) {
            conds.add("wt.assigned_at >= :dateFrom");
            params.addValue("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("wt.assigned_at <= :dateTo");
            params.addValue("dateTo", dateTo.atTime(23, 59, 59));
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
                .completedAt(toLocalDateTime(rs, "completed_at"))
                .totalTimeLoggedMinutes(getNullableInt(rs, "total_time_logged_minutes"))
                .dynamicDeadline(toLocalDateTime(rs, "dynamic_deadline"))
                .submissionNotes(rs.getString("submission_notes"))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .updatedAt(toLocalDateTime(rs, "updated_at"))
                .reworkCount(getNullableInt(rs, "rework_count"))
                .requestorReworkCount(getNullableInt(rs, "requestor_rework_count"))
                .latestManagerReworkComment(rs.getString("latest_manager_rework_comment"))
                .latestRequestorReworkComment(rs.getString("latest_requestor_rework_comment"))
                .latestActionDoneByName(rs.getString("latest_action_done_by_name"))
                .campaignDeadline(rs.getDate("campaign_deadline") == null ? null : rs.getDate("campaign_deadline").toLocalDate())
                .campaignPriority(safeEnum(Priority.class, rs.getString("campaign_priority")))
                .campaignStatus(safeEnum(CampaignStatus.class, rs.getString("campaign_status")))
                .requestorId(getNullableInt(rs, "requestor_id"))
                .requestorName(rs.getString("requestor_name"))
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
