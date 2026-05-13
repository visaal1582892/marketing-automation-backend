package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.DynamicQuestion;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.enums.FieldType;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence layer for {@code dynamic_questions}.
 * Uses NamedParameterJdbcTemplate with raw SQL — no JPA/Hibernate.
 * Primary key format: QUES-1, QUES-2, …
 */
@Repository
public class DynamicQuestionRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DynamicQuestionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Inserts a new dynamic question with an auto-generated {@code QUES-X} PK.
     *
     * @return the saved question (with questionId populated)
     */
    public DynamicQuestion save(DynamicQuestion q) {
        String newId = generateNextQuestionId();
        q.setQuestionId(newId);
        jdbc.update("""
                INSERT INTO dynamic_questions
                    (question_id, question_text, field_type, options, is_required)
                VALUES
                    (:id, :text, :type, :options, :required)
                """,
                new MapSqlParameterSource()
                        .addValue("id",       newId)
                        .addValue("text",     q.getQuestionText())
                        .addValue("type",     q.getFieldType() == null ? null : q.getFieldType().name())
                        .addValue("options",  q.getOptions())
                        .addValue("required", q.isRequired()));
        return q;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    public Optional<DynamicQuestion> findById(String questionId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT * FROM dynamic_questions WHERE question_id = :id",
                    new MapSqlParameterSource("id", questionId),
                    DynamicQuestionRepository::map));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<DynamicQuestion> findAll() {
        return jdbc.query("SELECT * FROM dynamic_questions ORDER BY COALESCE(updated_at, created_at) DESC",
                DynamicQuestionRepository::map);
    }

    /**
     * All questions with mapped granular task IDs and names (GROUP_CONCAT) for admin UI.
     */
    public List<Map<String, Object>> findAllWithMappings() {
        return jdbc.queryForList("""
                SELECT dq.question_id,
                       dq.question_text,
                       dq.field_type,
                       dq.options,
                       dq.is_required,
                       GROUP_CONCAT(tqm.granular_task_id ORDER BY tqm.granular_task_id SEPARATOR ',')  AS task_ids,
                       GROUP_CONCAT(gt.task_name         ORDER BY tqm.granular_task_id SEPARATOR '||') AS task_names
                  FROM dynamic_questions dq
                  LEFT JOIN task_question_mapping tqm ON tqm.question_id    = dq.question_id
                  LEFT JOIN granular_tasks        gt  ON gt.task_id         = tqm.granular_task_id
                 GROUP BY dq.question_id, dq.question_text, dq.field_type, dq.options, dq.is_required
                 ORDER BY COALESCE(dq.updated_at, dq.created_at) DESC
                """,
                new MapSqlParameterSource());
    }

    /** Paged + filtered questions with task mappings for the admin Question Library table. */
    public PagedResponse<Map<String, Object>> findAllWithMappingsPaged(String questionText,
                                                                         int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        List<String> havingConds = new ArrayList<>();

        if (questionText != null && !questionText.isBlank()) {
            havingConds.add("question_text LIKE :questionText");
            params.addValue("questionText", "%" + questionText.trim() + "%");
        }

        String having = havingConds.isEmpty() ? "" : " HAVING " + String.join(" AND ", havingConds);

        String baseSql = """
                SELECT dq.question_id,
                       dq.question_text,
                       dq.field_type,
                       dq.options,
                       dq.is_required,
                       GROUP_CONCAT(tqm.granular_task_id ORDER BY tqm.granular_task_id SEPARATOR ',')  AS task_ids,
                       GROUP_CONCAT(gt.task_name         ORDER BY tqm.granular_task_id SEPARATOR '||') AS task_names
                  FROM dynamic_questions dq
                  LEFT JOIN task_question_mapping tqm ON tqm.question_id    = dq.question_id
                  LEFT JOIN granular_tasks        gt  ON gt.task_id         = tqm.granular_task_id
                 GROUP BY dq.question_id, dq.question_text, dq.field_type, dq.options, dq.is_required
                """ + having;

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM (" + baseSql + ") AS counted",
                params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<Map<String, Object>> content = jdbc.queryForList(
                baseSql + " ORDER BY COALESCE(dq.updated_at, dq.created_at) DESC"
                        + " LIMIT :_size OFFSET :_offset",
                params);

        return PagedResponse.of(content, total, page, size);
    }

    public int update(DynamicQuestion q) {
        return jdbc.update("""
                UPDATE dynamic_questions
                   SET question_text = :text,
                       field_type    = :type,
                       options       = :options,
                       is_required   = :required
                 WHERE question_id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("id",       q.getQuestionId())
                        .addValue("text",     q.getQuestionText())
                        .addValue("type",     q.getFieldType() == null ? null : q.getFieldType().name())
                        .addValue("options",  q.getOptions())
                        .addValue("required", q.isRequired()));
    }

    public int delete(String questionId) {
        return jdbc.update(
                "DELETE FROM dynamic_questions WHERE question_id = :id",
                new MapSqlParameterSource("id", questionId));
    }

    /**
     * Returns the questions mapped to a given work task via the
     * {@code task_question_mapping} join table.
     *
     * The mapping is keyed on {@code granular_task_id}: a work task's
     * granular task determines which questions apply.
     *
     * @param workTaskId the WORK-TASK-X identifier of the work task
     */
    public List<DynamicQuestion> findByWorkTaskId(String workTaskId) {
        return jdbc.query("""
                SELECT dq.*
                  FROM dynamic_questions dq
                  JOIN task_question_mapping tqm ON tqm.question_id    = dq.question_id
                  JOIN work_tasks           wt  ON wt.granular_task_id = tqm.granular_task_id
                 WHERE wt.task_id = :taskId
                 ORDER BY dq.question_id
                """,
                new MapSqlParameterSource("taskId", workTaskId),
                DynamicQuestionRepository::map);
    }

    /**
     * Questions mapped to a granular task type (for the request form before any work task exists).
     */
    public List<DynamicQuestion> findByGranularTaskId(String granularTaskId) {
        return jdbc.query("""
                SELECT dq.*
                  FROM dynamic_questions dq
                  JOIN task_question_mapping tqm ON tqm.question_id = dq.question_id
                 WHERE tqm.granular_task_id = :taskId
                 ORDER BY dq.question_id
                """,
                new MapSqlParameterSource("taskId", granularTaskId),
                DynamicQuestionRepository::map);
    }

    // -------------------------------------------------------------------------
    // Custom ID generation
    // -------------------------------------------------------------------------

    /**
     * Queries the highest numeric suffix of existing QUES-X IDs and returns
     * the next one. Synchronized to avoid duplicates under concurrent inserts.
     */
    private synchronized String generateNextQuestionId() {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(question_id, 6) AS UNSIGNED)) " +
                "FROM dynamic_questions WHERE question_id LIKE 'QUES-%'",
                new MapSqlParameterSource(),
                Integer.class);
        int next = (max == null) ? 1 : max + 1;
        return "QUES-" + next;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static DynamicQuestion map(ResultSet rs, int rowNum) throws SQLException {
        String type = rs.getString("field_type");
        return DynamicQuestion.builder()
                .questionId(rs.getString("question_id"))
                .questionText(rs.getString("question_text"))
                .fieldType(type == null ? null : FieldType.valueOf(type))
                .options(rs.getString("options"))
                .isRequired(rs.getBoolean("is_required"))
                .build();
    }
}
