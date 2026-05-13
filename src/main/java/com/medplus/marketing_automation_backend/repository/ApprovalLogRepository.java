package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.ApprovalLog;
import com.medplus.marketing_automation_backend.enums.ApprovalAction;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ApprovalLogRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ApprovalLogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(ApprovalLog log) {
        jdbc.update("""
                INSERT INTO approvals_log (task_id, reviewer_id, action_taken, comments)
                VALUES (:taskId, :reviewerId, :action, :comments)
                """,
                new MapSqlParameterSource()
                        .addValue("taskId",     log.getTaskId())
                        .addValue("reviewerId", log.getReviewerId())
                        .addValue("action",     log.getActionTaken() == null ? null : log.getActionTaken().name())
                        .addValue("comments",   log.getComments()));
    }

    // ── Rework statistics ──────────────────────────────────────────────────────

    /**
     * Per-worker rework summary — how many times each worker's tasks were sent
     * back for rework, and how many distinct tasks were affected.
     *
     * rework_count        : total NEEDS_REWORK actions against this worker's tasks
     * tasks_with_rework   : distinct task IDs that received ≥1 rework
     * last_rework_at      : most recent rework timestamp (UTC)
     */
    public List<Map<String, Object>> reworkStatsByWorker() {
        return jdbc.queryForList("""
                SELECT  u.user_id,
                        u.full_name                        AS assignee_name,
                        r.role_name,
                        COUNT(al.log_id)                   AS rework_count,
                        COUNT(DISTINCT al.task_id)         AS tasks_with_rework,
                        MAX(al.created_at)                 AS last_rework_at
                FROM    approvals_log al
                JOIN    work_tasks wt ON wt.task_id     = al.task_id
                JOIN    users      u  ON u.user_id      = wt.assigned_to
                LEFT JOIN roles    r  ON r.role_id      = u.role_id
                WHERE   al.action_taken = 'NEEDS_REWORK'
                GROUP BY u.user_id, u.full_name, r.role_name
                ORDER BY rework_count DESC, u.full_name
                """,
                new MapSqlParameterSource());
    }

    /**
     * Per-task rework detail — every task that has been sent for rework at least
     * once, with the most-recent reviewer comment for quick triage.
     *
     * rework_count         : how many times this specific task was reworked
     * last_rework_at       : timestamp of the most recent rework
     * last_rework_comment  : reviewer's comment from the latest rework action
     * last_reviewer_name   : name of the reviewer who last sent it for rework
     */
    public List<Map<String, Object>> reworkStatsByTask() {
        return jdbc.queryForList("""
                SELECT  wt.task_id,
                        wt.campaign_id,
                        COALESCE(gt.task_name, 'Unknown') AS task_name,
                        u.full_name                       AS assignee_name,
                        wt.status,
                        COUNT(al.log_id)                  AS rework_count,
                        MAX(al.created_at)                AS last_rework_at,
                        (SELECT al2.comments
                           FROM approvals_log al2
                          WHERE al2.task_id      = wt.task_id
                            AND al2.action_taken = 'NEEDS_REWORK'
                          ORDER BY al2.log_id DESC
                          LIMIT 1)                        AS last_rework_comment,
                        (SELECT u2.full_name
                           FROM approvals_log al3
                           JOIN users u2 ON u2.user_id = al3.reviewer_id
                          WHERE al3.task_id      = wt.task_id
                            AND al3.action_taken = 'NEEDS_REWORK'
                          ORDER BY al3.log_id DESC
                          LIMIT 1)                        AS last_reviewer_name
                FROM    work_tasks    wt
                JOIN    approvals_log al  ON al.task_id      = wt.task_id
                                        AND al.action_taken  = 'NEEDS_REWORK'
                LEFT JOIN granular_tasks gt ON gt.task_id    = wt.granular_task_id
                LEFT JOIN users          u  ON u.user_id     = wt.assigned_to
                GROUP BY wt.task_id, wt.campaign_id, gt.task_name, u.full_name, wt.status
                HAVING  COUNT(al.log_id) > 0
                ORDER BY rework_count DESC, last_rework_at DESC
                """,
                new MapSqlParameterSource());
    }

    public List<ApprovalLog> findByTaskId(String taskId) {
        return jdbc.query("""
                SELECT al.log_id, al.task_id, al.reviewer_id, al.action_taken,
                       al.comments, al.created_at, u.full_name AS reviewer_name
                FROM approvals_log al
                LEFT JOIN users u ON u.user_id = al.reviewer_id
                WHERE al.task_id = :taskId
                ORDER BY al.log_id DESC
                """,
                new MapSqlParameterSource("taskId", taskId),
                (rs, rowNum) -> {
                    String action = rs.getString("action_taken");
                    Timestamp ts  = rs.getTimestamp("created_at");
                    return ApprovalLog.builder()
                            .logId(rs.getInt("log_id"))
                            .taskId(rs.getString("task_id"))
                            .reviewerId(rs.getInt("reviewer_id"))
                            .reviewerName(rs.getString("reviewer_name"))
                            .actionTaken(action == null ? null : ApprovalAction.valueOf(action))
                            .comments(rs.getString("comments"))
                            .createdAt(ts == null ? null : ts.toLocalDateTime())
                            .build();
                });
    }

    public Optional<ApprovalLog> findLatestByTaskIdAndAction(String taskId, ApprovalAction action) {
        List<ApprovalLog> rows = jdbc.query("""
                SELECT al.log_id, al.task_id, al.reviewer_id, al.action_taken,
                       al.comments, al.created_at, u.full_name AS reviewer_name
                FROM approvals_log al
                LEFT JOIN users u ON u.user_id = al.reviewer_id
                WHERE al.task_id = :taskId
                  AND al.action_taken = :action
                ORDER BY al.log_id DESC
                LIMIT 1
                """,
                new MapSqlParameterSource("taskId", taskId)
                        .addValue("action", action == null ? null : action.name()),
                (rs, rowNum) -> {
                    String actionTaken = rs.getString("action_taken");
                    Timestamp ts = rs.getTimestamp("created_at");
                    return ApprovalLog.builder()
                            .logId(rs.getInt("log_id"))
                            .taskId(rs.getString("task_id"))
                            .reviewerId(rs.getInt("reviewer_id"))
                            .reviewerName(rs.getString("reviewer_name"))
                            .actionTaken(actionTaken == null ? null : ApprovalAction.valueOf(actionTaken))
                            .comments(rs.getString("comments"))
                            .createdAt(ts == null ? null : ts.toLocalDateTime())
                            .build();
                });
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }
}
