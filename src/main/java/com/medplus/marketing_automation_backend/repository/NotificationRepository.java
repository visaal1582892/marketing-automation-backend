package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.Notification;
import com.medplus.marketing_automation_backend.domain.NotificationTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class NotificationRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public NotificationRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    public Notification insert(long userId, String eventType, String message, String url) {
        String sql = """
                INSERT INTO notifications (user_id, event_type, message, url)
                VALUES (:userId, :eventType, :message, :url)
                """;
        var kh = new GeneratedKeyHolder();
        jdbc.update(sql,
                new MapSqlParameterSource()
                        .addValue("userId",    userId)
                        .addValue("eventType", eventType)
                        .addValue("message",   message)
                        .addValue("url",       url),
                kh);
        return findById(kh.getKey().longValue());
    }

    public List<Notification> findByUserId(long userId) {
        String sql = """
                SELECT id, user_id, event_type, message, url, is_read, created_at
                FROM notifications
                WHERE user_id = :userId
                ORDER BY created_at DESC
                LIMIT 100
                """;
        return jdbc.query(sql, Map.of("userId", userId), NotificationRepository::mapNotification);
    }

    public int countUnreadByUserId(long userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND is_read = 0";
        Integer count = jdbc.queryForObject(sql, Map.of("userId", userId), Integer.class);
        return count == null ? 0 : count;
    }

    public int markRead(long notificationId, long userId) {
        String sql = """
                UPDATE notifications SET is_read = 1
                WHERE id = :id AND user_id = :userId
                """;
        return jdbc.update(sql, Map.of("id", notificationId, "userId", userId));
    }

    public int markAllRead(long userId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = :userId AND is_read = 0";
        return jdbc.update(sql, Map.of("userId", userId));
    }

    private Notification findById(long id) {
        return jdbc.queryForObject(
                "SELECT id, user_id, event_type, message, url, is_read, created_at FROM notifications WHERE id = :id",
                Map.of("id", id),
                NotificationRepository::mapNotification);
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    public List<NotificationTemplate> findAllTemplates() {
        String sql = """
                SELECT id, event_type, role_id, message_template, url_template, created_at, updated_at
                FROM notification_templates
                ORDER BY event_type, ISNULL(role_id) DESC, role_id
                """;
        return jdbc.query(sql, Map.of(), NotificationRepository::mapTemplate);
    }

    /** Find a role-specific template (exact role_id match). */
    public Optional<NotificationTemplate> findTemplateByEventAndRole(String eventType, String roleId) {
        if (roleId == null) return Optional.empty();
        String sql = """
                SELECT id, event_type, role_id, message_template, url_template, created_at, updated_at
                FROM notification_templates
                WHERE event_type = :eventType AND role_id = :roleId
                """;
        List<NotificationTemplate> rows = jdbc.query(sql,
                Map.of("eventType", eventType, "roleId", roleId),
                NotificationRepository::mapTemplate);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Find the default template (role_id IS NULL). */
    public Optional<NotificationTemplate> findDefaultTemplate(String eventType) {
        String sql = """
                SELECT id, event_type, role_id, message_template, url_template, created_at, updated_at
                FROM notification_templates
                WHERE event_type = :eventType AND role_id IS NULL
                """;
        List<NotificationTemplate> rows = jdbc.query(sql,
                Map.of("eventType", eventType),
                NotificationRepository::mapTemplate);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int updateTemplate(long id, String messageTemplate, String urlTemplate) {
        String sql = """
                UPDATE notification_templates
                SET message_template = :msg, url_template = :url
                WHERE id = :id
                """;
        return jdbc.update(sql, Map.of("id", id, "msg", messageTemplate, "url", urlTemplate));
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private static Notification mapNotification(ResultSet rs, int rowNum) throws SQLException {
        Timestamp ts = rs.getTimestamp("created_at");
        return Notification.builder()
                .id(rs.getLong("id"))
                .userId(rs.getLong("user_id"))
                .eventType(rs.getString("event_type"))
                .message(rs.getString("message"))
                .url(rs.getString("url"))
                .read(rs.getBoolean("is_read"))
                .createdAt(ts == null ? null : ts.toLocalDateTime())
                .build();
    }

    private static NotificationTemplate mapTemplate(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        return NotificationTemplate.builder()
                .id(rs.getLong("id"))
                .eventType(rs.getString("event_type"))
                .roleId(rs.getString("role_id"))
                .messageTemplate(rs.getString("message_template"))
                .urlTemplate(rs.getString("url_template"))
                .createdAt(created == null ? null : created.toLocalDateTime())
                .updatedAt(updated == null ? null : updated.toLocalDateTime())
                .build();
    }
}
