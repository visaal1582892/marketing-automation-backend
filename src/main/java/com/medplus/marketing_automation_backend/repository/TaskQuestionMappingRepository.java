package com.medplus.marketing_automation_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Manages the {@code task_question_mapping} join table that maps
 * {@code granular_task_id} entries to {@code question_id} entries.
 */
@Repository
public class TaskQuestionMappingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public TaskQuestionMappingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Links a granular task type to a dynamic question.
     * Uses INSERT IGNORE so re-running a seed script is safe.
     */
    public void map(String granularTaskId, String questionId) {
        jdbc.update("""
                INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id)
                VALUES (:taskId, :questionId)
                """,
                new MapSqlParameterSource()
                        .addValue("taskId",     granularTaskId)
                        .addValue("questionId", questionId));
    }

    /** Returns all question IDs mapped to a given granular task type. */
    public List<String> findQuestionIdsByGranularTaskId(String granularTaskId) {
        return jdbc.queryForList(
                "SELECT question_id FROM task_question_mapping WHERE granular_task_id = :taskId",
                new MapSqlParameterSource("taskId", granularTaskId),
                String.class);
    }

    /** Removes a specific mapping. */
    public void unmap(String granularTaskId, String questionId) {
        jdbc.update("""
                DELETE FROM task_question_mapping
                WHERE granular_task_id = :taskId AND question_id = :questionId
                """,
                new MapSqlParameterSource()
                        .addValue("taskId",     granularTaskId)
                        .addValue("questionId", questionId));
    }

    public void deleteAllForQuestion(String questionId) {
        jdbc.update(
                "DELETE FROM task_question_mapping WHERE question_id = :qId",
                new MapSqlParameterSource("qId", questionId));
    }
}
