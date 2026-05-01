package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.WorkTask;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class WorkTaskRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_BASE = """
            SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                   wt.granular_task_id,
                   wt.status,
                   wt.assigned_at, wt.accepted_at, wt.started_at,
                   wt.submitted_at, wt.completed_at,
                   wt.total_time_logged_minutes, wt.dynamic_deadline,
                   wt.submission_notes, wt.asset_url, wt.worker_comment,
                   wt.created_at, wt.updated_at,
                   u.full_name    AS assignee_name,
                   gt.task_name  AS granular_task_name,
                   tt.task_name  AS task_type_name,
                   c.deadline    AS campaign_deadline,
                   c.priority    AS campaign_priority,
                   c.status      AS campaign_status,
                   rt.requirement_name,
                   req.full_name AS requestor_name,
                   (SELECT COUNT(*) FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'NEEDS_REWORK') AS rework_count,
                   (SELECT COUNT(*) FROM approvals_log al
                     WHERE al.task_id = wt.task_id
                       AND al.action_taken = 'REQUESTOR_REWORK') AS requestor_rework_count
            FROM work_tasks wt
            LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
            LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
            LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
            LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
            LEFT JOIN requirement_types rt  ON rt.requirement_type_id = c.requirement_type_id
            LEFT JOIN users             req ON req.user_id            = c.requestor_id
            """;

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
                        String submissionNotes, String assetUrl) {
        return jdbc.update("""
                UPDATE work_tasks
                SET status = 'QC_REVIEW',
                    submitted_at = :submittedAt,
                    total_time_logged_minutes = :minutes,
                    submission_notes = :notes,
                    asset_url        = :assetUrl
                WHERE task_id = :id
                  AND assigned_to = :userId
                  AND status = 'IN_PROGRESS'
                """,
                new MapSqlParameterSource("submittedAt", Timestamp.valueOf(submittedAt))
                        .addValue("minutes",  minutesSpent)
                        .addValue("notes",    submissionNotes)
                        .addValue("assetUrl", assetUrl)
                        .addValue("id",       taskId)
                        .addValue("userId",   userId));
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
     * Saves a worker comment and flips the task to HELD.
     * Allowed from ASSIGNED, IN_PROGRESS, or REWORK — the worker is pausing their
     * own task to flag a blocker or ask a question to the requestor.
     */
    public int saveWorkerComment(String taskId, int userId, String comment) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET worker_comment = :comment,
                       status         = 'HELD'
                 WHERE task_id     = :id
                   AND assigned_to = :uid
                   AND status IN ('ASSIGNED','IN_PROGRESS','REWORK')
                """,
                new MapSqlParameterSource("id",      taskId)
                        .addValue("uid",     userId)
                        .addValue("comment", comment));
    }

    /**
     * Clears the worker comment and returns the task to ASSIGNED.
     * Called when the worker decides to resume work (their question was answered).
     */
    public int clearWorkerComment(String taskId, int userId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET worker_comment = NULL,
                       status         = 'ASSIGNED',
                       assigned_at    = :now
                 WHERE task_id     = :id
                   AND assigned_to = :uid
                   AND status      = 'HELD'
                   AND worker_comment IS NOT NULL
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("uid", userId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
    }

    /**
     * Clears the worker comment and resumes a HELD task — called by the
     * requestor path when they save updated answers, acknowledging the comment.
     * No userId guard is needed here because the caller is the requestor,
     * not the assigned worker.
     */
    public int clearWorkerCommentByTaskId(String taskId) {
        return jdbc.update("""
                UPDATE work_tasks
                   SET worker_comment = NULL,
                       status         = 'ASSIGNED',
                       assigned_at    = :now
                 WHERE task_id        = :id
                   AND status         = 'HELD'
                   AND worker_comment IS NOT NULL
                """,
                new MapSqlParameterSource("id",  taskId)
                        .addValue("now", Timestamp.valueOf(LocalDateTime.now())));
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
                LEFT JOIN roles      r  ON r.role_id = u.role_id
                LEFT JOIN work_tasks wt ON wt.assigned_to = u.user_id
                WHERE u.status = 'ACTIVE'
                GROUP BY u.user_id, u.full_name, r.role_name
                ORDER BY minutes_logged DESC, u.full_name
                """,
                new MapSqlParameterSource("from", fromCutoff).addValue("to", toCutoff));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Custom ID generation
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
                .assignedAt(toLocalDateTime(rs, "assigned_at"))
                .acceptedAt(toLocalDateTime(rs, "accepted_at"))
                .startedAt(toLocalDateTime(rs, "started_at"))
                .submittedAt(toLocalDateTime(rs, "submitted_at"))
                .completedAt(toLocalDateTime(rs, "completed_at"))
                .totalTimeLoggedMinutes(getNullableInt(rs, "total_time_logged_minutes"))
                .dynamicDeadline(toLocalDateTime(rs, "dynamic_deadline"))
                .submissionNotes(rs.getString("submission_notes"))
                .assetUrl(rs.getString("asset_url"))
                .workerComment(rs.getString("worker_comment"))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .updatedAt(toLocalDateTime(rs, "updated_at"))
                .reworkCount(getNullableInt(rs, "rework_count"))
                .requestorReworkCount(getNullableInt(rs, "requestor_rework_count"))
                .campaignDeadline(rs.getDate("campaign_deadline") == null ? null : rs.getDate("campaign_deadline").toLocalDate())
                .campaignPriority(safeEnum(Priority.class, rs.getString("campaign_priority")))
                .campaignStatus(safeEnum(CampaignStatus.class, rs.getString("campaign_status")))
                .requirementTypeName(rs.getString("requirement_name"))
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
