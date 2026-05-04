package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.TaskMessage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class TaskMessageRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TaskMessageRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert a new message and return it with its generated ID and timestamps. */
    public TaskMessage insert(String taskId, int userId, String message) {
        String sql = """
                INSERT INTO task_messages (task_id, user_id, message)
                VALUES (:taskId, :userId, :message)
                """;
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql,
                new MapSqlParameterSource()
                        .addValue("taskId",  taskId)
                        .addValue("userId",  userId)
                        .addValue("message", message),
                kh);
        int newId = kh.getKey().intValue();
        return findById(newId);
    }

    /** All messages for a task, oldest first. */
    public List<TaskMessage> findByTaskId(String taskId) {
        String sql = """
                SELECT tm.message_id, tm.task_id, tm.user_id,
                       u.full_name AS user_name,
                       tm.message, tm.created_at, tm.updated_at
                FROM task_messages tm
                JOIN users u ON u.user_id = tm.user_id
                WHERE tm.task_id = :taskId
                ORDER BY tm.created_at ASC
                """;
        return jdbc.query(sql, Map.of("taskId", taskId), TaskMessageRepository::map);
    }

    private TaskMessage findById(int messageId) {
        String sql = """
                SELECT tm.message_id, tm.task_id, tm.user_id,
                       u.full_name AS user_name,
                       tm.message, tm.created_at, tm.updated_at
                FROM task_messages tm
                JOIN users u ON u.user_id = tm.user_id
                WHERE tm.message_id = :id
                """;
        return jdbc.queryForObject(sql, Map.of("id", messageId), TaskMessageRepository::map);
    }

    private static TaskMessage map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return TaskMessage.builder()
                .messageId(rs.getInt("message_id"))
                .taskId(rs.getString("task_id"))
                .userId(rs.getInt("user_id"))
                .userName(rs.getString("user_name"))
                .message(rs.getString("message"))
                .createdAt(created == null ? null : created.toLocalDateTime())
                .updatedAt(updated == null ? null : updated.toLocalDateTime())
                .build();
    }
}
