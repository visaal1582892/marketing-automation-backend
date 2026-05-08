package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.TaskCollaborator;
import com.medplus.marketing_automation_backend.domain.WorkTask;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.Priority;
import com.medplus.marketing_automation_backend.enums.TaskStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class CollaboratorRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CollaboratorRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Add a list of collaborators to a task (ignores duplicates). */
    public void addCollaborators(String taskId, List<Integer> userIds) {
        if (userIds == null || userIds.isEmpty()) return;
        String sql = """
                INSERT IGNORE INTO task_collaborators (task_id, user_id)
                VALUES (:taskId, :userId)
                """;
        MapSqlParameterSource[] batch = userIds.stream()
                .map(uid -> new MapSqlParameterSource()
                        .addValue("taskId", taskId)
                        .addValue("userId", uid))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    /** List all collaborators on a task, ordered by added_at. */
    public List<TaskCollaborator> findByTaskId(String taskId) {
        String sql = """
                SELECT tc.id, tc.task_id, tc.user_id, tc.added_at,
                       u.full_name AS user_name, u.email AS user_email,
                       d.designation_name
                FROM task_collaborators tc
                JOIN users        u ON u.user_id        = tc.user_id
                LEFT JOIN designations d ON d.designation_id = u.designation_id
                WHERE tc.task_id = :taskId
                ORDER BY tc.added_at
                """;
        return jdbc.query(sql, Map.of("taskId", taskId), CollaboratorRepository::mapCollaborator);
    }

    /** Returns true if the given user is a collaborator on the task. */
    public boolean isCollaborator(String taskId, int userId) {
        String sql = """
                SELECT COUNT(*) FROM task_collaborators
                WHERE task_id = :taskId AND user_id = :userId
                """;
        Integer count = jdbc.queryForObject(sql,
                new MapSqlParameterSource("taskId", taskId).addValue("userId", userId),
                Integer.class);
        return count != null && count > 0;
    }

    /**
     * Returns all work tasks assigned to userId that have at least one
     * collaborator, ordered newest-assigned first.
     */
    public List<WorkTask> findOwnedTasksWithCollaborators(int userId) {
        String sql = """
                SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                       wt.granular_task_id, wt.status,
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
                       c.requestor_id AS requestor_id,
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
                FROM work_tasks wt
                LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
                LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
                LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
                LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
                LEFT JOIN users             req ON req.user_id            = c.requestor_id
                WHERE wt.assigned_to = :userId
                  AND EXISTS (
                      SELECT 1 FROM task_collaborators tc WHERE tc.task_id = wt.task_id
                  )
                ORDER BY wt.assigned_at DESC
                """;
        return jdbc.query(sql, Map.of("userId", userId), CollaboratorRepository::mapWorkTask);
    }

    /**
     * Returns all work tasks on which the given user is a collaborator,
     * ordered newest-assigned first.
     */
    public List<WorkTask> findTasksByCollaboratorUserId(int userId) {
        String sql = """
                SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                       wt.granular_task_id, wt.status,
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
                       c.requestor_id AS requestor_id,
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
                FROM task_collaborators tc
                JOIN work_tasks wt ON wt.task_id = tc.task_id
                LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
                LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
                LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
                LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
                LEFT JOIN users             req ON req.user_id            = c.requestor_id
                WHERE tc.user_id = :userId
                ORDER BY wt.assigned_at DESC
                """;

        return jdbc.query(sql, Map.of("userId", userId), CollaboratorRepository::mapWorkTask);
    }

    /**
     * All tasks where the campaign's requestor_id matches the given userId
     * AND the task has at least one collaborator — used so requestors see
     * every collaboration on their campaigns even if not explicitly invited.
     */
    public List<WorkTask> findTasksByRequestorId(int requestorId) {
        String sql = """
                SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                       wt.granular_task_id, wt.status,
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
                       c.requestor_id AS requestor_id,
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
                FROM work_tasks wt
                LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
                LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
                LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
                LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
                LEFT JOIN users             req ON req.user_id            = c.requestor_id
                WHERE c.requestor_id = :requestorId
                  AND wt.status NOT IN ('CANCELLED')
                  AND EXISTS (SELECT 1 FROM task_collaborators tc WHERE tc.task_id = wt.task_id)
                ORDER BY wt.created_at DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource("requestorId", requestorId),
                CollaboratorRepository::mapWorkTask);
    }

    // -------------------------------------------------------------------------
    // Row mappers
    // -------------------------------------------------------------------------

    private static TaskCollaborator mapCollaborator(ResultSet rs, int rowNum) throws SQLException {
        return TaskCollaborator.builder()
                .id(rs.getInt("id"))
                .taskId(rs.getString("task_id"))
                .userId(rs.getInt("user_id"))
                .userName(rs.getString("user_name"))
                .userEmail(rs.getString("user_email"))
                .designationName(rs.getString("designation_name"))
                .addedAt(toLocalDateTime(rs, "added_at"))
                .build();
    }

    /**
     * Counts active collaborations visible to the given user in a single query.
     * A collaboration is "active" when is_collaboration_started = 1 AND
     * is_collaboration_active = 1.  The user sees a task if they are:
     *   - the assigned worker, OR
     *   - listed in task_collaborators, OR
     *   - the campaign's requestor
     * (Admins/managers get the same count via the collaborator row that is
     * auto-inserted when collaboration starts, so they aren't under-counted.)
     */
    public int countActiveForUser(int userId) {
        String sql = """
                SELECT COUNT(DISTINCT wt.task_id)
                FROM work_tasks wt
                LEFT JOIN campaigns c ON c.campaign_id = wt.campaign_id
                WHERE wt.is_collaboration_started = 1
                  AND wt.is_collaboration_active  = 1
                  AND wt.status NOT IN ('CANCELLED','COMPLETED')
                  AND (
                      wt.assigned_to = :userId
                      OR EXISTS (
                          SELECT 1 FROM task_collaborators tc
                          WHERE tc.task_id = wt.task_id AND tc.user_id = :userId
                      )
                      OR c.requestor_id = :userId
                  )
                """;
        Integer count = jdbc.queryForObject(sql, Map.of("userId", userId), Integer.class);
        return count != null ? count : 0;
    }

    /** Add a single collaborator (idempotent — uses INSERT IGNORE). */
    public void addSingleCollaborator(String taskId, int userId) {
        jdbc.update("""
                INSERT IGNORE INTO task_collaborators (task_id, user_id)
                VALUES (:taskId, :userId)
                """,
                new MapSqlParameterSource("taskId", taskId).addValue("userId", userId));
    }

    /**
     * Returns all work tasks that have at least one collaborator and are in an
     * open-chat status (IN_PROGRESS or REWORK). Used for the admin view which
     * shows every active collaboration.
     */
    public List<WorkTask> findAllTasksWithOpenChat() {
        String sql = """
                SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                       wt.granular_task_id, wt.status,
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
                       c.requestor_id AS requestor_id,
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
                FROM work_tasks wt
                LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
                LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
                LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
                LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
                LEFT JOIN users             req ON req.user_id            = c.requestor_id
                WHERE wt.status IN ('IN_PROGRESS','REWORK')
                  AND EXISTS (SELECT 1 FROM task_collaborators tc WHERE tc.task_id = wt.task_id)
                ORDER BY wt.assigned_at DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource(), CollaboratorRepository::mapWorkTask);
    }

    /**
     * All tasks that have at least one collaborator — used by Marketing Managers
     * who can see and participate in every collaboration regardless of status.
     */
    public List<WorkTask> findAllTasksWithAnyCollaborator() {
        String sql = """
                SELECT wt.task_id, wt.campaign_id, wt.assigned_to,
                       wt.granular_task_id, wt.status,
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
                       c.requestor_id AS requestor_id,
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
                FROM work_tasks wt
                LEFT JOIN users             u   ON u.user_id              = wt.assigned_to
                LEFT JOIN granular_tasks    gt  ON gt.task_id             = wt.granular_task_id
                LEFT JOIN task_types        tt  ON tt.task_type_id        = gt.task_type_id
                LEFT JOIN campaigns         c   ON c.campaign_id          = wt.campaign_id
                LEFT JOIN users             req ON req.user_id            = c.requestor_id
                WHERE wt.status NOT IN ('CANCELLED')
                  AND EXISTS (SELECT 1 FROM task_collaborators tc WHERE tc.task_id = wt.task_id)
                ORDER BY wt.created_at DESC
                """;
        return jdbc.query(sql, new MapSqlParameterSource(), CollaboratorRepository::mapWorkTask);
    }

    private static WorkTask mapWorkTask(ResultSet rs, int rowNum) throws SQLException {
        return WorkTask.builder()
                .taskId(rs.getString("task_id"))
                .campaignId(rs.getInt("campaign_id"))
                .assignedTo(getNullableInt(rs, "assigned_to"))
                .requestorId(getNullableInt(rs, "requestor_id"))
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
                .campaignDeadline(rs.getDate("campaign_deadline") == null ? null
                        : rs.getDate("campaign_deadline").toLocalDate())
                .campaignPriority(safeEnum(Priority.class, rs.getString("campaign_priority")))
                .campaignStatus(safeEnum(CampaignStatus.class, rs.getString("campaign_status")))
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
        try { return Enum.valueOf(clazz, value); } catch (IllegalArgumentException e) { return null; }
    }
}
