package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.QcRouting;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class QcRoutingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public QcRoutingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** All current QC routing mappings. */
    public List<QcRouting> findAll() {
        return jdbc.query(
                "SELECT id, worker_role_id, manager_role_id FROM qc_routing ORDER BY worker_role_id, manager_role_id",
                Collections.emptyMap(),
                (rs, rn) -> QcRouting.builder()
                        .id(rs.getInt("id"))
                        .workerRoleId(rs.getString("worker_role_id"))
                        .managerRoleId(rs.getString("manager_role_id"))
                        .build());
    }

    /**
     * Replaces the entire routing config atomically.
     * Deletes all existing rows then inserts the new list.
     */
    public void replaceAll(List<QcRouting> mappings) {
        jdbc.update("DELETE FROM qc_routing", Collections.emptyMap());
        if (mappings == null || mappings.isEmpty()) return;
        for (QcRouting m : mappings) {
            jdbc.update(
                    "INSERT IGNORE INTO qc_routing (worker_role_id, manager_role_id) VALUES (:w, :m)",
                    new MapSqlParameterSource("w", m.getWorkerRoleId())
                            .addValue("m", m.getManagerRoleId()));
        }
    }

    /**
     * Returns the worker role IDs that route to ANY of the given manager role IDs.
     * Used when building a manager's QC queue filter.
     * Returns an empty list if no mappings exist for the given manager roles
     * (caller interprets empty as "show all").
     */
    public List<String> findWorkerRolesForManagerRoles(List<String> managerRoleIds) {
        if (managerRoleIds == null || managerRoleIds.isEmpty()) return Collections.emptyList();
        return jdbc.queryForList(
                "SELECT DISTINCT worker_role_id FROM qc_routing WHERE manager_role_id IN (:ids)",
                new MapSqlParameterSource("ids", managerRoleIds),
                String.class);
    }

    /**
     * All roles with their IDs and names, excluding the given non-worker role names.
     * Used by the controller to populate the frontend matrix.
     */
    public List<Map<String, Object>> findRolesExcluding(List<String> excludedRoleNames) {
        if (excludedRoleNames == null || excludedRoleNames.isEmpty()) {
            return jdbc.queryForList(
                    "SELECT role_id, role_name FROM roles WHERE status = 'ACTIVE' ORDER BY role_name",
                    Collections.emptyMap());
        }
        return jdbc.queryForList(
                "SELECT role_id, role_name FROM roles WHERE status = 'ACTIVE' AND role_name NOT IN (:names) ORDER BY role_name",
                new MapSqlParameterSource("names", excludedRoleNames));
    }

    /** Returns roles whose names match the given list (case-sensitive). */
    public List<Map<String, Object>> findRolesByNames(List<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) return Collections.emptyList();
        return jdbc.queryForList(
                "SELECT role_id, role_name FROM roles WHERE role_name IN (:names) ORDER BY role_name",
                new MapSqlParameterSource("names", roleNames));
    }
}
