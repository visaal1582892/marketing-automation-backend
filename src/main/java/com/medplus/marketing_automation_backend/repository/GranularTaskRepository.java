package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.GranularTask;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.TaskCategory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class GranularTaskRepository {

    private static final String SELECT_BASE = """
            SELECT gt.task_id, gt.task_name, gt.task_type_id,
                   tt.task_name AS task_type_name,
                   gt.task_category,
                   gt.status
            FROM granular_tasks gt
            LEFT JOIN task_types tt ON tt.task_type_id = gt.task_type_id
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public GranularTaskRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<GranularTask> findAll(boolean includeInactive) {
        String sql = SELECT_BASE
                + (includeInactive ? "" : " WHERE gt.status = 'ACTIVE'")
                + " ORDER BY COALESCE(gt.updated_at, gt.created_at) DESC";
        return jdbc.query(sql, mapper());
    }

    public List<GranularTask> findByTaskType(String taskTypeId) {
        String sql = SELECT_BASE
                + " WHERE gt.task_type_id = :taskTypeId AND gt.status = 'ACTIVE'"
                + " ORDER BY COALESCE(gt.updated_at, gt.created_at) DESC";
        return jdbc.query(sql, new MapSqlParameterSource("taskTypeId", taskTypeId), mapper());
    }

    /** Paged + filtered list for the admin Granular Tasks table. */
    public PagedResponse<GranularTask> findAllPaged(String taskId, String taskName,
                                                     String taskTypeName, String status,
                                                     int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> conds = new ArrayList<>();

        if (taskId != null && !taskId.isBlank()) {
            conds.add("gt.task_id LIKE :taskId");
            params.addValue("taskId", "%" + taskId.trim() + "%");
        }
        if (taskName != null && !taskName.isBlank()) {
            conds.add("gt.task_name LIKE :taskName");
            params.addValue("taskName", "%" + taskName.trim() + "%");
        }
        if (taskTypeName != null && !taskTypeName.isBlank()) {
            conds.add("tt.task_name = :taskTypeName");
            params.addValue("taskTypeName", taskTypeName.trim());
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            conds.add("gt.status = :status");
            params.addValue("status", status.trim().toUpperCase());
        }

        String where = conds.isEmpty() ? "" : " WHERE " + String.join(" AND ", conds);
        String countSql = "SELECT COUNT(*) FROM granular_tasks gt"
                + " LEFT JOIN task_types tt ON tt.task_type_id = gt.task_type_id" + where;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<GranularTask> content = jdbc.query(
                SELECT_BASE + where
                        + " ORDER BY COALESCE(gt.updated_at, gt.created_at) DESC"
                        + " LIMIT :_size OFFSET :_offset",
                params, mapper());

        return PagedResponse.of(content, total, page, size);
    }

    public Optional<GranularTask> findById(String id) {
        try {
            return Optional.ofNullable(
                    jdbc.queryForObject(SELECT_BASE + " WHERE gt.task_id = :id",
                            new MapSqlParameterSource("id", id), mapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public String insert(GranularTask task) {
        String maxSql = "SELECT COALESCE(MAX(CAST(SUBSTRING(task_id, 6) AS UNSIGNED)), 0) FROM granular_tasks FOR UPDATE";
        Long max = jdbc.getJdbcOperations().queryForObject(maxSql, Long.class);
        String newId = "TASK-" + ((max == null ? 0L : max) + 1L);

        RecordStatus status = task.getStatus() == null ? RecordStatus.ACTIVE : task.getStatus();
        jdbc.update("""
                INSERT INTO granular_tasks (task_id, task_name, task_type_id, task_category, status)
                VALUES (:id, :name, :taskTypeId, :taskCategory, :status)
                """,
                new MapSqlParameterSource()
                        .addValue("id",           newId)
                        .addValue("name",          task.getTaskName())
                        .addValue("taskTypeId",    task.getTaskTypeId())
                        .addValue("taskCategory",  task.getTaskCategory() == null ? null : task.getTaskCategory().name())
                        .addValue("status",        status.name()));
        return newId;
    }

    public int update(String id, GranularTask task) {
        RecordStatus status = task.getStatus() == null ? RecordStatus.ACTIVE : task.getStatus();
        return jdbc.update("""
                UPDATE granular_tasks
                SET task_name = :name, task_type_id = :taskTypeId, task_category = :taskCategory, status = :status
                WHERE task_id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("name",         task.getTaskName())
                        .addValue("taskTypeId",   task.getTaskTypeId())
                        .addValue("taskCategory", task.getTaskCategory() == null ? null : task.getTaskCategory().name())
                        .addValue("status",       status.name())
                        .addValue("id",           id));
    }

    /** Hard-delete: permanently removes the granular task from the database. */
    public int delete(String id) {
        return jdbc.update("DELETE FROM granular_tasks WHERE task_id = :id",
                new MapSqlParameterSource("id", id));
    }

    public boolean existsByName(String name, String excludingId) {
        String sql = "SELECT COUNT(*) FROM granular_tasks WHERE LOWER(task_name) = LOWER(:name)"
                + (excludingId == null ? "" : " AND task_id <> :id");
        MapSqlParameterSource p = new MapSqlParameterSource("name", name);
        if (excludingId != null) p.addValue("id", excludingId);
        Integer count = jdbc.queryForObject(sql, p, Integer.class);
        return count != null && count > 0;
    }

    private static RowMapper<GranularTask> mapper() {
        return (rs, rowNum) -> {
            String rawStatus   = rs.getString("status");
            String rawCategory = rs.getString("task_category");
            RecordStatus status       = rawStatus   == null ? RecordStatus.ACTIVE : RecordStatus.valueOf(rawStatus);
            TaskCategory taskCategory = rawCategory == null ? null : TaskCategory.valueOf(rawCategory);
            return GranularTask.builder()
                    .taskId(rs.getString("task_id"))
                    .taskName(rs.getString("task_name"))
                    .taskTypeId(rs.getString("task_type_id"))
                    .taskTypeName(rs.getString("task_type_name"))
                    .taskCategory(taskCategory)
                    .status(status)
                    .build();
        };
    }
}
