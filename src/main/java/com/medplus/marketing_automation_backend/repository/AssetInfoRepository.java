package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.AssetInfo;
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
public class AssetInfoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AssetInfoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert one asset URL for a task+user. */
    public void insert(String taskId, int userId, String url) {
        String sql = """
                INSERT INTO asset_info (task_id, user_id, url)
                VALUES (:taskId, :userId, :url)
                """;
        jdbc.update(sql,
                new MapSqlParameterSource()
                        .addValue("taskId", taskId)
                        .addValue("userId", userId)
                        .addValue("url",    url));
    }

    /** Insert multiple asset URLs in a batch. */
    public void insertAll(String taskId, int userId, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;
        String sql = """
                INSERT INTO asset_info (task_id, user_id, url)
                VALUES (:taskId, :userId, :url)
                """;
        MapSqlParameterSource[] batch = urls.stream()
                .map(url -> new MapSqlParameterSource()
                        .addValue("taskId", taskId)
                        .addValue("userId", userId)
                        .addValue("url",    url))
                .toArray(MapSqlParameterSource[]::new);
        jdbc.batchUpdate(sql, batch);
    }

    /** All assets for a task, oldest first. */
    public List<AssetInfo> findByTaskId(String taskId) {
        String sql = """
                SELECT ai.asset_id, ai.task_id, ai.user_id,
                       u.full_name AS user_name,
                       ai.url, ai.created_at
                FROM asset_info ai
                JOIN users u ON u.user_id = ai.user_id
                WHERE ai.task_id = :taskId
                ORDER BY ai.created_at ASC
                """;
        return jdbc.query(sql, Map.of("taskId", taskId), AssetInfoRepository::map);
    }

    private static AssetInfo map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return AssetInfo.builder()
                .assetId(rs.getInt("asset_id"))
                .taskId(rs.getString("task_id"))
                .userId(rs.getInt("user_id"))
                .userName(rs.getString("user_name"))
                .url(rs.getString("url"))
                .createdAt(created == null ? null : created.toLocalDateTime())
                .build();
    }
}
