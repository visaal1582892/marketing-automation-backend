package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.RoleTask;
import com.medplus.marketing_automation_backend.domain.RoutingRule;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class RoutingConfigRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public RoutingConfigRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Requirement → Role mappings
    // -------------------------------------------------------------------------

    public List<RoutingRule> findAllRequirementRoleMappings() {
        return jdbc.query("""
                SELECT m.mapping_id, m.requirement_type_id, rt.requirement_name,
                       m.default_role_id, r.role_name
                FROM requirement_role_mapping m
                JOIN requirement_types rt ON rt.requirement_type_id = m.requirement_type_id
                JOIN roles r              ON r.role_id              = m.default_role_id
                ORDER BY m.mapping_id
                """, (rs, rowNum) -> RoutingRule.builder()
                .mappingId(rs.getInt("mapping_id"))
                .requirementTypeId(rs.getString("requirement_type_id"))
                .requirementTypeName(rs.getString("requirement_name"))
                .defaultRoleId(rs.getString("default_role_id"))
                .defaultRoleName(rs.getString("role_name"))
                .build());
    }

    public Optional<RoutingRule> findRequirementRoleMappingById(int mappingId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT m.mapping_id, m.requirement_type_id, rt.requirement_name,
                           m.default_role_id, r.role_name
                    FROM requirement_role_mapping m
                    JOIN requirement_types rt ON rt.requirement_type_id = m.requirement_type_id
                    JOIN roles r              ON r.role_id              = m.default_role_id
                    WHERE m.mapping_id = :id
                    """, new MapSqlParameterSource("id", mappingId),
                    (rs, rowNum) -> RoutingRule.builder()
                            .mappingId(rs.getInt("mapping_id"))
                            .requirementTypeId(rs.getString("requirement_type_id"))
                            .requirementTypeName(rs.getString("requirement_name"))
                            .defaultRoleId(rs.getString("default_role_id"))
                            .defaultRoleName(rs.getString("role_name"))
                            .build()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Returns the default role ID configured for the given requirement type, if any. */
    public Optional<String> findDefaultRoleForRequirement(String requirementTypeId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT default_role_id FROM requirement_role_mapping WHERE requirement_type_id = :id",
                    new MapSqlParameterSource("id", requirementTypeId),
                    String.class));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /** Insert or replace the mapping for a requirement type (upsert). */
    public void upsertRequirementRoleMapping(String requirementTypeId, String roleId) {
        jdbc.update("""
                INSERT INTO requirement_role_mapping (requirement_type_id, default_role_id)
                VALUES (:reqId, :roleId)
                ON DUPLICATE KEY UPDATE default_role_id = :roleId
                """,
                new MapSqlParameterSource()
                        .addValue("reqId",  requirementTypeId)
                        .addValue("roleId", roleId));
    }

    public int deleteRequirementRoleMapping(int mappingId) {
        return jdbc.update("DELETE FROM requirement_role_mapping WHERE mapping_id = :id",
                new MapSqlParameterSource("id", mappingId));
    }

    // -------------------------------------------------------------------------
    // Role → Task mappings
    // -------------------------------------------------------------------------

    public List<RoleTask> findAllRoleTaskMappings() {
        return jdbc.query("""
                SELECT m.mapping_id, m.role_id, r.role_name, m.task_id, gt.task_name, m.status
                FROM role_task_mapping m
                JOIN roles          r  ON r.role_id  = m.role_id
                JOIN granular_tasks gt ON gt.task_id = m.task_id
                ORDER BY m.role_id, m.task_id
                """, roleTaskMapper());
    }

    /**
     * Returns the list of ACTIVE roles configured to handle the given granular task.
     * Used by the routing engine when assigning a deliverable to a creator.
     */
    public List<String> findActiveRoleIdsForTask(String taskId) {
        return jdbc.queryForList("""
                SELECT m.role_id
                FROM role_task_mapping m
                JOIN roles r ON r.role_id = m.role_id
                WHERE m.task_id = :taskId
                  AND m.status = 'ACTIVE'
                  AND r.status = 'ACTIVE'
                ORDER BY m.mapping_id
                """,
                new MapSqlParameterSource("taskId", taskId),
                String.class);
    }

    public List<RoleTask> findRoleTaskMappingsByRole(String roleId) {
        return jdbc.query("""
                SELECT m.mapping_id, m.role_id, r.role_name, m.task_id, gt.task_name, m.status
                FROM role_task_mapping m
                JOIN roles          r  ON r.role_id  = m.role_id
                JOIN granular_tasks gt ON gt.task_id = m.task_id
                WHERE m.role_id = :roleId
                ORDER BY m.task_id
                """, new MapSqlParameterSource("roleId", roleId), roleTaskMapper());
    }

    public void addRoleTaskMapping(String roleId, String taskId) {
        jdbc.update("""
                INSERT IGNORE INTO role_task_mapping (role_id, task_id) VALUES (:roleId, :taskId)
                """,
                new MapSqlParameterSource()
                        .addValue("roleId", roleId)
                        .addValue("taskId", taskId));
    }

    public int updateRoleTaskStatus(int mappingId, RecordStatus status) {
        return jdbc.update(
                "UPDATE role_task_mapping SET status = :status WHERE mapping_id = :id",
                new MapSqlParameterSource()
                        .addValue("status", status.name())
                        .addValue("id", mappingId));
    }

    public Optional<RoleTask> findRoleTaskMappingById(int mappingId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject("""
                    SELECT m.mapping_id, m.role_id, r.role_name, m.task_id, gt.task_name, m.status
                    FROM role_task_mapping m
                    JOIN roles          r  ON r.role_id  = m.role_id
                    JOIN granular_tasks gt ON gt.task_id = m.task_id
                    WHERE m.mapping_id = :id
                    """, new MapSqlParameterSource("id", mappingId), roleTaskMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int deleteRoleTaskMapping(int mappingId) {
        return jdbc.update("DELETE FROM role_task_mapping WHERE mapping_id = :id",
                new MapSqlParameterSource("id", mappingId));
    }

    private static org.springframework.jdbc.core.RowMapper<RoleTask> roleTaskMapper() {
        return (rs, rowNum) -> RoleTask.builder()
                .mappingId(rs.getInt("mapping_id"))
                .roleId(rs.getString("role_id"))
                .roleName(rs.getString("role_name"))
                .taskId(rs.getString("task_id"))
                .taskName(rs.getString("task_name"))
                .status(RecordStatus.valueOf(rs.getString("status")))
                .build();
    }
}
