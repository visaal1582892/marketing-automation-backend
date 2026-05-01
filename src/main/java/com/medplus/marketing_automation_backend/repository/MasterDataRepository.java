package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.enums.MasterTableType;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Single generic repository for all master lookup tables defined in
 * {@link MasterTableType}.  IDs are sequential numeric strings ("1","2",…)
 * computed via MAX(CAST(id AS UNSIGNED)) + 1 on insert.
 * Table/column identifiers come from the trusted enum — no SQL injection risk.
 */
@Repository
public class MasterDataRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public MasterDataRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public List<MasterItem> findAll(MasterTableType type, boolean includeInactive) {
        String sql = "SELECT " + type.idColumn() + " AS id, "
                + type.nameColumn() + " AS name, status "
                + "FROM " + type.tableName()
                + (includeInactive ? "" : " WHERE status = 'ACTIVE'")
                + " ORDER BY CAST(" + type.idColumn() + " AS UNSIGNED) ASC";
        return jdbc.query(sql, mapper());
    }

    public Optional<MasterItem> findById(MasterTableType type, String id) {
        String sql = "SELECT " + type.idColumn() + " AS id, "
                + type.nameColumn() + " AS name, status "
                + "FROM " + type.tableName()
                + " WHERE " + type.idColumn() + " = :id";
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, new MapSqlParameterSource("id", id), mapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Mutations
    // -------------------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRED)
    public String insert(MasterTableType type, MasterItem item) {
        String maxSql = "SELECT COALESCE(MAX(CAST(" + type.idColumn() + " AS UNSIGNED)), 0) "
                + "FROM " + type.tableName() + " FOR UPDATE";
        Long maxId = jdbc.getJdbcOperations().queryForObject(maxSql, Long.class);
        String newId = String.valueOf((maxId == null ? 0L : maxId) + 1L);

        RecordStatus status = item.getStatus() == null ? RecordStatus.ACTIVE : item.getStatus();
        String sql = "INSERT INTO " + type.tableName()
                + " (" + type.idColumn() + ", " + type.nameColumn() + ", status)"
                + " VALUES (:id, :name, :status)";
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id",     newId)
                .addValue("name",   item.getName())
                .addValue("status", status.name()));
        return newId;
    }

    public int update(MasterTableType type, String id, MasterItem item) {
        RecordStatus status = item.getStatus() == null ? RecordStatus.ACTIVE : item.getStatus();
        String sql = "UPDATE " + type.tableName()
                + " SET " + type.nameColumn() + " = :name, status = :status"
                + " WHERE " + type.idColumn() + " = :id";
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("name",   item.getName())
                .addValue("status", status.name())
                .addValue("id",     id));
    }

    public int delete(MasterTableType type, String id) {
        return jdbc.update(
                "DELETE FROM " + type.tableName() + " WHERE " + type.idColumn() + " = :id",
                new MapSqlParameterSource("id", id));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public boolean existsByName(MasterTableType type, String name, String excludingId) {
        String sql = "SELECT COUNT(*) FROM " + type.tableName()
                + " WHERE LOWER(" + type.nameColumn() + ") = LOWER(:name)"
                + (excludingId == null ? "" : " AND " + type.idColumn() + " <> :id");
        MapSqlParameterSource p = new MapSqlParameterSource("name", name);
        if (excludingId != null) p.addValue("id", excludingId);
        Integer count = jdbc.queryForObject(sql, p, Integer.class);
        return count != null && count > 0;
    }

    public Optional<MasterItem> findByName(MasterTableType type, String name) {
        String sql = "SELECT " + type.idColumn() + " AS id, "
                + type.nameColumn() + " AS name, status "
                + "FROM " + type.tableName()
                + " WHERE LOWER(" + type.nameColumn() + ") = LOWER(:name)";
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(sql, new MapSqlParameterSource("name", name), mapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    private static RowMapper<MasterItem> mapper() {
        return (rs, rowNum) -> {
            String raw = rs.getString("status");
            RecordStatus status = raw == null ? RecordStatus.ACTIVE : RecordStatus.valueOf(raw);
            return MasterItem.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .status(status)
                    .build();
        };
    }
}
