package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.WorkerComment;
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
public class WorkerCommentRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkerCommentRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert a new worker comment for a task. */
    public void insert(String taskId, int userId, String comment) {
        jdbc.update("""
                INSERT INTO worker_comments (task_id, user_id, comment)
                VALUES (:taskId, :userId, :comment)
                """,
                new MapSqlParameterSource()
                        .addValue("taskId",  taskId)
                        .addValue("userId",  userId)
                        .addValue("comment", comment));
    }

    /** Fetch all unanswered comments for a task, newest first. */
    public List<WorkerComment> findActiveByTaskId(String taskId) {
        return jdbc.query("""
                SELECT wc.comment_id, wc.task_id, wc.user_id,
                       u.full_name AS user_name,
                       wc.comment, wc.is_answered, wc.created_at
                FROM worker_comments wc
                JOIN users u ON u.user_id = wc.user_id
                WHERE wc.task_id = :taskId AND wc.is_answered = 0
                ORDER BY wc.created_at DESC
                """,
                Map.of("taskId", taskId),
                WorkerCommentRepository::map);
    }

    /** Mark a single comment as answered. */
    public void markAnswered(int commentId) {
        jdbc.update("""
                UPDATE worker_comments SET is_answered = 1 WHERE comment_id = :id
                """,
                Map.of("id", commentId));
    }

    /** Mark ALL active comments on a task as answered (called on task unhold). */
    public void markAllAnswered(String taskId) {
        jdbc.update("""
                UPDATE worker_comments SET is_answered = 1
                WHERE task_id = :taskId AND is_answered = 0
                """,
                Map.of("taskId", taskId));
    }

    private static WorkerComment map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return WorkerComment.builder()
                .commentId(rs.getInt("comment_id"))
                .taskId(rs.getString("task_id"))
                .userId(rs.getInt("user_id"))
                .userName(rs.getString("user_name"))
                .comment(rs.getString("comment"))
                .answered(rs.getBoolean("is_answered"))
                .createdAt(ts == null ? null : ts.toLocalDateTime())
                .build();
    }
}
