package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.AssetInfo;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class AssetInfoRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public AssetInfoRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Insert one asset for a task+user, with optional thumbnail and original filename. */
    public void insert(String taskId, int userId, String url, String thumbnailUrl, String originalFilename) {
        jdbc.update("""
                INSERT INTO asset_info (task_id, user_id, url, thumbnail_url, original_filename)
                VALUES (:taskId, :userId, :url, :thumbnailUrl, :originalFilename)
                """,
                new MapSqlParameterSource()
                        .addValue("taskId",           taskId)
                        .addValue("userId",           userId)
                        .addValue("url",              url)
                        .addValue("thumbnailUrl",     thumbnailUrl)
                        .addValue("originalFilename", originalFilename));
    }

    /** Fetch a single asset by its primary key. */
    public Optional<AssetInfo> findById(int assetId) {
        List<AssetInfo> rows = jdbc.query("""
                SELECT ai.asset_id, ai.task_id, ai.user_id,
                       u.full_name AS user_name,
                       ai.url, ai.thumbnail_url, ai.original_filename, ai.created_at
                FROM asset_info ai
                JOIN users u ON u.user_id = ai.user_id
                WHERE ai.asset_id = :id
                """,
                new MapSqlParameterSource("id", assetId),
                AssetInfoRepository::map);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** Delete an asset only if the caller is the owner. Returns rows affected (0 = denied). */
    public int deleteByIdAndUserId(int assetId, int userId) {
        return jdbc.update("""
                DELETE FROM asset_info WHERE asset_id = :id AND user_id = :uid
                """,
                new MapSqlParameterSource("id", assetId).addValue("uid", userId));
    }

    /** All assets for a task, oldest first, joined with uploader name. */
    public List<AssetInfo> findByTaskId(String taskId) {
        return jdbc.query("""
                SELECT ai.asset_id, ai.task_id, ai.user_id,
                       u.full_name AS user_name,
                       ai.url, ai.thumbnail_url, ai.original_filename, ai.created_at
                FROM asset_info ai
                JOIN users u ON u.user_id = ai.user_id
                WHERE ai.task_id = :taskId
                ORDER BY ai.created_at ASC
                """,
                Map.of("taskId", taskId),
                AssetInfoRepository::map);
    }

    private static AssetInfo map(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return AssetInfo.builder()
                .assetId(rs.getInt("asset_id"))
                .taskId(rs.getString("task_id"))
                .userId(rs.getInt("user_id"))
                .userName(rs.getString("user_name"))
                .url(rs.getString("url"))
                .thumbnailUrl(rs.getString("thumbnail_url"))
                .originalFilename(rs.getString("original_filename"))
                .createdAt(created == null ? null : created.toLocalDateTime())
                .build();
    }
}
