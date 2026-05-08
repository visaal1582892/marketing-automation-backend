package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.SkillLevel;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class UserRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public UserRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── SELECT_BASE no longer joins roles — roles come from user_roles junction ──
    private static final String SELECT_BASE = """
            SELECT u.user_id, u.full_name, u.email, u.password_hash,
                   u.department_id, d.department_name,
                   u.designation_id, dsg.designation_name,
                   u.skill_level, u.current_active_tasks,
                   u.status, u.created_at
            FROM users u
            LEFT JOIN departments  d   ON d.department_id    = u.department_id
            LEFT JOIN designations dsg ON dsg.designation_id = u.designation_id
            """;

    // ── Role fetching via junction table ────────────────────────────────────────

    /**
     * Fetches all (role_id, role_name) pairs for a given user from user_roles.
     * Results are ordered by role_id for a stable, deterministic primary-role pick.
     */
    private List<String[]> findRolesByUserId(long userId) {
        return jdbc.query("""
                SELECT ur.role_id, r.role_name
                  FROM user_roles ur
                  JOIN roles r ON r.role_id = ur.role_id
                 WHERE ur.user_id = :userId
                 ORDER BY ur.role_id
                """,
                new MapSqlParameterSource("userId", userId),
                (rs, rn) -> new String[]{rs.getString("role_id"), rs.getString("role_name")});
    }

    /**
     * Enriches a list of User objects with their roles from user_roles.
     * Uses a single batch query to avoid N+1.
     */
    private void enrichWithRoles(List<User> users) {
        if (users.isEmpty()) return;
        List<Long> ids = users.stream().map(User::getUserId).collect(Collectors.toList());

        // batch fetch all role rows for these users
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ur.user_id, ur.role_id, r.role_name
                  FROM user_roles ur
                  JOIN roles r ON r.role_id = ur.role_id
                 WHERE ur.user_id IN (:ids)
                 ORDER BY ur.user_id, ur.role_id
                """,
                new MapSqlParameterSource("ids", ids));

        // group by userId
        Map<Long, List<String>> roleIdsMap   = new HashMap<>();
        Map<Long, List<String>> roleNamesMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            long uid = ((Number) row.get("user_id")).longValue();
            roleIdsMap  .computeIfAbsent(uid, k -> new ArrayList<>()).add((String) row.get("role_id"));
            roleNamesMap.computeIfAbsent(uid, k -> new ArrayList<>()).add((String) row.get("role_name"));
        }

        for (User u : users) {
            u.setRoleIds  (roleIdsMap  .getOrDefault(u.getUserId(), Collections.emptyList()));
            u.setRoleNames(roleNamesMap.getOrDefault(u.getUserId(), Collections.emptyList()));
        }
    }

    private void enrichWithRoles(User user) {
        if (user == null) return;
        List<String[]> pairs = findRolesByUserId(user.getUserId());
        user.setRoleIds  (pairs.stream().map(p -> p[0]).collect(Collectors.toList()));
        user.setRoleNames(pairs.stream().map(p -> p[1]).collect(Collectors.toList()));
    }

    // ── Writes to user_roles ────────────────────────────────────────────────────

    /** Inserts all roleIds for a user into the junction table. Silently skips duplicates. */
    public void insertUserRoles(long userId, List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) return;
        for (String roleId : roleIds) {
            jdbc.update(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (:uid, :rid)",
                    new MapSqlParameterSource("uid", userId).addValue("rid", roleId));
        }
    }

    /** Replaces all roles for a user — deletes existing entries then inserts the new list. */
    public void replaceUserRoles(long userId, List<String> roleIds) {
        jdbc.update("DELETE FROM user_roles WHERE user_id = :uid",
                new MapSqlParameterSource("uid", userId));
        insertUserRoles(userId, roleIds);
    }

    // ── Basic lookups ────────────────────────────────────────────────────────────

    public Optional<User> findByEmail(String email) {
        try {
            User u = jdbc.queryForObject(
                    SELECT_BASE + " WHERE u.email = :email",
                    new MapSqlParameterSource("email", email),
                    UserRepository::map);
            enrichWithRoles(u);
            return Optional.ofNullable(u);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<User> findById(Long id) {
        try {
            User u = jdbc.queryForObject(
                    SELECT_BASE + " WHERE u.user_id = :id",
                    new MapSqlParameterSource("id", id),
                    UserRepository::map);
            enrichWithRoles(u);
            return Optional.ofNullable(u);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    /**
     * Inserts a new user row (without role_id — roles are in user_roles).
     * Caller must also call {@link #insertUserRoles} after this.
     *
     * @return the generated user_id
     */
    public Long create(User user) {
        String sql = """
                INSERT INTO users (full_name, email, password_hash, department_id,
                                   designation_id, skill_level, current_active_tasks, status)
                VALUES (:fullName, :email, :passwordHash, :departmentId,
                        :designationId, :skillLevel, :currentActiveTasks, :status)
                """;
        RecordStatus status = user.getStatus() == null ? RecordStatus.ACTIVE : user.getStatus();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("fullName",           user.getFullName())
                .addValue("email",              user.getEmail())
                .addValue("passwordHash",       user.getPasswordHash())
                .addValue("departmentId",       user.getDepartmentId())
                .addValue("designationId",      user.getDesignationId())
                .addValue("skillLevel",         user.getSkillLevel() == null ? null : user.getSkillLevel().name())
                .addValue("currentActiveTasks", user.getCurrentActiveTasks() == null ? 0 : user.getCurrentActiveTasks())
                .addValue("status",             status.name());
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, p, kh, new String[]{"user_id"});
        Long newId = kh.getKey() == null ? null : kh.getKey().longValue();
        if (newId != null && user.getRoleIds() != null) {
            insertUserRoles(newId, user.getRoleIds());
        }
        return newId;
    }

    // ── Additional read operations ───────────────────────────────────────────────

    public List<User> findAll(boolean includeInactive) {
        String where = includeInactive ? "" : " WHERE u.status = 'ACTIVE'";
        List<User> users = jdbc.query(SELECT_BASE + where + " ORDER BY u.user_id", UserRepository::map);
        enrichWithRoles(users);
        return users;
    }

    /** Active users who hold the given role. */
    public List<User> findByRole(String roleId) {
        List<User> users = jdbc.query(
                SELECT_BASE + """
                        JOIN user_roles ur ON ur.user_id = u.user_id
                        WHERE ur.role_id = :roleId
                          AND u.status   = 'ACTIVE'
                        ORDER BY u.user_id
                        """,
                new MapSqlParameterSource("roleId", roleId),
                UserRepository::map);
        enrichWithRoles(users);
        return users;
    }

    /**
     * Returns all active marketing team workers — users who hold at least one
     * "worker" role (any role that is NOT Admin, Requestor, Marketing Manager, Head,
     * or Regional Manager). Multi-role users who have at least one worker role
     * are included so TASK-OTHER manual assignment finds them correctly.
     */
    public List<User> findAllMarketingWorkers() {
        List<User> users = jdbc.query(
                SELECT_BASE + """
                        WHERE u.status = 'ACTIVE'
                          AND EXISTS (
                              SELECT 1
                                FROM user_roles ur
                                JOIN roles r ON r.role_id = ur.role_id
                               WHERE ur.user_id = u.user_id
                                 AND r.role_name NOT IN (
                                     'Admin', 'Requestor', 'Marketing Manager', 'Head', 'Regional Manager'
                                 )
                          )
                        ORDER BY u.current_active_tasks ASC, u.user_id
                        """,
                UserRepository::map);
        enrichWithRoles(users);
        return users;
    }

    /**
     * Returns the active user in the given role with the lowest live workload.
     * The picked row is locked with FOR UPDATE to prevent concurrent double-assignment.
     */
    public Optional<User> findBestAvailableUserInRole(String roleId) {
        return findBestAvailableUserInRole(roleId, -1);
    }

    /**
     * Like {@link #findBestAvailableUserInRole(String)} but excludes a specific user
     * (used to prevent routing a campaign's tasks back to its own requestor).
     *
     * @param excludeUserId user_id to skip; pass {@code -1} to skip no one
     */
    public Optional<User> findBestAvailableUserInRole(String roleId, int excludeUserId) {
        try {
            String excludeClause = excludeUserId > 0 ? " AND u.user_id != :excludeUserId" : "";
            User u = jdbc.queryForObject(
                    SELECT_BASE + """
                              JOIN user_roles ur ON ur.user_id = u.user_id
                              LEFT JOIN (
                                  SELECT assigned_to, COUNT(*) AS active_count
                                    FROM work_tasks
                                   WHERE assigned_to IS NOT NULL
                                     AND status IN ('ASSIGNED','IN_PROGRESS','REWORK','QC_REVIEW')
                                   GROUP BY assigned_to
                              ) wt ON wt.assigned_to = u.user_id
                         WHERE ur.role_id  = :roleId
                           AND u.status    = 'ACTIVE'
                         """ + excludeClause + """
                         ORDER BY COALESCE(wt.active_count, 0) ASC, u.user_id ASC
                         LIMIT 1
                         FOR UPDATE
                        """,
                    new MapSqlParameterSource("roleId", roleId)
                            .addValue("excludeUserId", excludeUserId),
                    UserRepository::map);
            enrichWithRoles(u);
            return Optional.ofNullable(u);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    /** Updates profile fields (no role_id — roles are managed via replaceUserRoles). */
    public int update(User user) {
        return jdbc.update("""
                UPDATE users
                   SET full_name      = :fullName,
                       email          = :email,
                       department_id  = :departmentId,
                       designation_id = :designationId,
                       skill_level    = :skillLevel
                 WHERE user_id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("fullName",      user.getFullName())
                        .addValue("email",         user.getEmail())
                        .addValue("departmentId",  user.getDepartmentId())
                        .addValue("designationId", user.getDesignationId())
                        .addValue("skillLevel",    user.getSkillLevel() == null ? null : user.getSkillLevel().name())
                        .addValue("id",            user.getUserId()));
    }

    public int updatePassword(Long userId, String passwordHash) {
        return jdbc.update(
                "UPDATE users SET password_hash = :hash WHERE user_id = :id",
                new MapSqlParameterSource("hash", passwordHash).addValue("id", userId));
    }

    public int updateStatus(Long userId, RecordStatus status) {
        return jdbc.update(
                "UPDATE users SET status = :status WHERE user_id = :id",
                new MapSqlParameterSource("status", status.name()).addValue("id", userId));
    }

    public int delete(Long userId) {
        return jdbc.update(
                "DELETE FROM users WHERE user_id = :id",
                new MapSqlParameterSource("id", userId));
    }

    public int incrementActiveTasks(Long userId) {
        return jdbc.update(
                "UPDATE users SET current_active_tasks = current_active_tasks + 1 WHERE user_id = :id",
                new MapSqlParameterSource("id", userId));
    }

    public int decrementActiveTasks(Long userId) {
        return jdbc.update(
                "UPDATE users SET current_active_tasks = GREATEST(current_active_tasks - 1, 0) WHERE user_id = :id",
                new MapSqlParameterSource("id", userId));
    }

    /**
     * Reconciles {@code users.current_active_tasks} against the actual number
     * of non-terminal rows in {@code work_tasks}.
     */
    public int reconcileActiveTaskCounters() {
        return jdbc.update("""
                UPDATE users u
                LEFT JOIN (
                    SELECT assigned_to AS user_id,
                           COUNT(*)    AS active_count
                      FROM work_tasks
                     WHERE assigned_to IS NOT NULL
                       AND status IN ('ASSIGNED','IN_PROGRESS','REWORK','QC_REVIEW')
                     GROUP BY assigned_to
                ) wt ON wt.user_id = u.user_id
                   SET u.current_active_tasks = COALESCE(wt.active_count, 0)
                """, new MapSqlParameterSource());
    }

    // ── Paged / filtered queries ─────────────────────────────────────────────────

    /**
     * Paginated user list with optional column-level filters.
     * Role filter matches any of the user's roles by role_name (exact).
     */
    public PagedResponse<User> findAllPaged(
            boolean includeInactive,
            String name, String email, String roleName,
            String departmentId, String designationId,
            String skillLevel, String status,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conds = new ArrayList<>();

        if (!includeInactive) {
            conds.add("u.status = 'ACTIVE'");
        }
        if (name != null && !name.isBlank()) {
            conds.add("u.full_name LIKE :name");
            params.addValue("name", "%" + name.trim() + "%");
        }
        if (email != null && !email.isBlank()) {
            conds.add("u.email LIKE :email");
            params.addValue("email", "%" + email.trim() + "%");
        }
        if (roleName != null && !roleName.isBlank()) {
            conds.add("""
                    EXISTS (
                        SELECT 1 FROM user_roles ur
                        JOIN roles r ON r.role_id = ur.role_id
                        WHERE ur.user_id = u.user_id AND r.role_name = :roleName
                    )""");
            params.addValue("roleName", roleName.trim());
        }
        if (departmentId != null && !departmentId.isBlank()) {
            conds.add("u.department_id = :departmentId");
            params.addValue("departmentId", departmentId.trim());
        }
        if (designationId != null && !designationId.isBlank()) {
            conds.add("u.designation_id = :designationId");
            params.addValue("designationId", designationId.trim());
        }
        if (skillLevel != null && !skillLevel.isBlank()) {
            conds.add("u.skill_level = :skillLevel");
            params.addValue("skillLevel", skillLevel.trim());
        }
        if (status != null && !status.isBlank()) {
            conds.add("u.status = :status");
            params.addValue("status", status.trim());
        }

        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
        String countSql = "SELECT COUNT(*) FROM users u" + where;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<User> content = jdbc.query(
                SELECT_BASE + where + " ORDER BY u.user_id LIMIT :_size OFFSET :_offset",
                params, UserRepository::map);
        enrichWithRoles(content);
        return PagedResponse.of(content, total, page, size);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    /** Maps a ResultSet row to a User — roles are NOT mapped here; call enrichWithRoles after. */
    private static User map(ResultSet rs, int rowNum) throws SQLException {
        String raw = rs.getString("status");
        RecordStatus status = raw == null ? RecordStatus.ACTIVE : RecordStatus.valueOf(raw);
        return User.builder()
                .userId(rs.getLong("user_id"))
                .fullName(rs.getString("full_name"))
                .email(rs.getString("email"))
                .passwordHash(rs.getString("password_hash"))
                .departmentId(rs.getString("department_id"))
                .departmentName(rs.getString("department_name"))
                .designationId(rs.getString("designation_id"))
                .designationName(rs.getString("designation_name"))
                .skillLevel(rs.getString("skill_level") == null ? null : SkillLevel.valueOf(rs.getString("skill_level")))
                .currentActiveTasks(getNullableInt(rs, "current_active_tasks"))
                .status(status)
                .createdAt(rs.getTimestamp("created_at") == null ? null
                        : rs.getTimestamp("created_at").toLocalDateTime())
                .build();
    }
}
