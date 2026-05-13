package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.AutoCreatedTask;
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
public class AutoCreatedTaskRepository {

    private static final String SELECT = """
            SELECT auto_created_task_id, source_task_id, created_task_id, campaign_id,
                   source_granular_task_id, content_granular_task_id,
                   requested_by_user_id, content_assignee_user_id, status,
                   created_at, updated_at
              FROM auto_created_tasks
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AutoCreatedTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(AutoCreatedTask row) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("sourceTaskId", row.getSourceTaskId())
                .addValue("createdTaskId", row.getCreatedTaskId())
                .addValue("campaignId", row.getCampaignId())
                .addValue("sourceGranularTaskId", row.getSourceGranularTaskId())
                .addValue("contentGranularTaskId", row.getContentGranularTaskId())
                .addValue("requestedByUserId", row.getRequestedByUserId())
                .addValue("contentAssigneeUserId", row.getContentAssigneeUserId())
                .addValue("status", row.getStatus());
        jdbc.update("""
                INSERT INTO auto_created_tasks
                    (source_task_id, created_task_id, campaign_id,
                     source_granular_task_id, content_granular_task_id,
                     requested_by_user_id, content_assignee_user_id, status)
                VALUES
                    (:sourceTaskId, :createdTaskId, :campaignId,
                     :sourceGranularTaskId, :contentGranularTaskId,
                     :requestedByUserId, :contentAssigneeUserId, :status)
                """, params);
        Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    public Optional<AutoCreatedTask> findBySourceTaskId(String sourceTaskId) {
        return findOne(SELECT + " WHERE source_task_id = :id",
                new MapSqlParameterSource("id", sourceTaskId));
    }

    public Optional<AutoCreatedTask> findByCreatedTaskId(String createdTaskId) {
        return findOne(SELECT + " WHERE created_task_id = :id",
                new MapSqlParameterSource("id", createdTaskId));
    }

    public List<AutoCreatedTask> findByCreatedTaskIds(List<String> createdTaskIds) {
        if (createdTaskIds == null || createdTaskIds.isEmpty()) return List.of();
        return jdbc.query(SELECT + " WHERE created_task_id IN (:ids)",
                new MapSqlParameterSource("ids", createdTaskIds),
                AutoCreatedTaskRepository::map);
    }

    public List<AutoCreatedTask> findBySourceTaskIds(List<String> sourceTaskIds) {
        if (sourceTaskIds == null || sourceTaskIds.isEmpty()) return List.of();
        return jdbc.query(SELECT + " WHERE source_task_id IN (:ids)",
                new MapSqlParameterSource("ids", sourceTaskIds),
                AutoCreatedTaskRepository::map);
    }

    public List<AutoCreatedTask> findByCampaignId(int campaignId) {
        return jdbc.query(SELECT + " WHERE campaign_id = :campaignId",
                new MapSqlParameterSource("campaignId", campaignId),
                AutoCreatedTaskRepository::map);
    }

    public int updateStatus(long autoCreatedTaskId, String status) {
        return jdbc.update("""
                UPDATE auto_created_tasks
                   SET status = :status,
                       updated_at = CURRENT_TIMESTAMP(6)
                 WHERE auto_created_task_id = :id
                """,
                new MapSqlParameterSource("status", status).addValue("id", autoCreatedTaskId));
    }

    private Optional<AutoCreatedTask> findOne(String sql, MapSqlParameterSource params) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, params, AutoCreatedTaskRepository::map));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static AutoCreatedTask map(ResultSet rs, int rowNum) throws SQLException {
        return AutoCreatedTask.builder()
                .autoCreatedTaskId(rs.getLong("auto_created_task_id"))
                .sourceTaskId(rs.getString("source_task_id"))
                .createdTaskId(rs.getString("created_task_id"))
                .campaignId(rs.getInt("campaign_id"))
                .sourceGranularTaskId(rs.getString("source_granular_task_id"))
                .contentGranularTaskId(rs.getString("content_granular_task_id"))
                .requestedByUserId(getNullableInt(rs, "requested_by_user_id"))
                .contentAssigneeUserId(getNullableInt(rs, "content_assignee_user_id"))
                .status(rs.getString("status"))
                .createdAt(toLocalDateTime(rs, "created_at"))
                .updatedAt(toLocalDateTime(rs, "updated_at"))
                .build();
    }

    private static LocalDateTime toLocalDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static Integer getNullableInt(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null || rs.wasNull()) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(value.toString());
    }
}
