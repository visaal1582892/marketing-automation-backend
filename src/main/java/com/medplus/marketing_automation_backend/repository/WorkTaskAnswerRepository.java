package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.WorkTaskAnswer;
import com.medplus.marketing_automation_backend.dto.WorkTaskAnswerBriefRow;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Persistence layer for {@code work_task_answers}.
 * Uses NamedParameterJdbcTemplate with raw SQL — no JPA/Hibernate.
 * Primary key format: ANS-1, ANS-2, …
 *
 * <p>Upsert semantics: if a worker re-submits an answer for the same
 * (work_task_id, question_id) pair, the value is updated in-place so
 * there is never more than one answer per question per task.
 */
@Repository
public class WorkTaskAnswerRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkTaskAnswerRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Inserts or updates a single answer.
     *
     * <ul>
     *   <li>New answer: generates {@code ANS-X} PK and inserts.
     *   <li>Existing answer: updates {@code answer_value} in-place.
     * </ul>
     *
     * @return the answer ID (either newly generated or the existing one)
     */
    public String save(WorkTaskAnswer answer) {
        // Check if an answer already exists for this (task, question) pair.
        String existingId = findAnswerId(answer.getWorkTaskId(), answer.getQuestionId());
        if (existingId != null) {
            jdbc.update("""
                    UPDATE work_task_answers
                       SET answer_value = :value
                     WHERE answer_id = :id
                    """,
                    new MapSqlParameterSource("value", answer.getAnswerValue())
                            .addValue("id", existingId));
            return existingId;
        }

        String newId = generateNextAnswerId();
        answer.setAnswerId(newId);
        jdbc.update("""
                INSERT INTO work_task_answers (answer_id, work_task_id, question_id, answer_value)
                VALUES (:id, :taskId, :questionId, :value)
                """,
                new MapSqlParameterSource()
                        .addValue("id",         newId)
                        .addValue("taskId",     answer.getWorkTaskId())
                        .addValue("questionId", answer.getQuestionId())
                        .addValue("value",      answer.getAnswerValue()));
        return newId;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns all answers submitted for a specific work task, ordered numerically
     *  so the most recent answer for each question is last (and wins in the
     *  frontend's answerMap loop). */
    public List<WorkTaskAnswer> findByWorkTaskId(String workTaskId) {
        return jdbc.query("""
                SELECT answer_id, work_task_id, question_id, answer_value
                  FROM work_task_answers
                 WHERE work_task_id = :taskId
                 ORDER BY CAST(SUBSTRING(answer_id, 5) AS UNSIGNED)
                """,
                new MapSqlParameterSource("taskId", workTaskId),
                WorkTaskAnswerRepository::map);
    }

    /**
     * All questionnaire answers for a campaign, with question text — for the request brief drawer.
     */
    public List<WorkTaskAnswerBriefRow> findQuestionnaireBriefForCampaign(int campaignId) {
        return jdbc.query("""
                SELECT wt.task_id AS work_task_id,
                       dq.question_id,
                       dq.question_text,
                       dq.field_type,
                       wta.answer_value
                  FROM work_task_answers wta
                  JOIN work_tasks wt ON wt.task_id = wta.work_task_id
                  JOIN dynamic_questions dq ON dq.question_id = wta.question_id
                 WHERE wt.campaign_id = :cid
                 ORDER BY wt.task_id, wta.answer_id
                """,
                new MapSqlParameterSource("cid", campaignId),
                (rs, rowNum) -> new WorkTaskAnswerBriefRow(
                        rs.getString("work_task_id"),
                        rs.getString("question_id"),
                        rs.getString("question_text"),
                        rs.getString("field_type"),
                        rs.getString("answer_value")));
    }

    // -------------------------------------------------------------------------
    // Custom ID generation
    // -------------------------------------------------------------------------

    /**
     * Queries the highest numeric suffix of existing ANS-X IDs and returns
     * the next one. Synchronized to avoid duplicates under concurrent inserts.
     */
    private synchronized String generateNextAnswerId() {
        Integer max = jdbc.queryForObject(
                "SELECT MAX(CAST(SUBSTRING(answer_id, 5) AS UNSIGNED)) " +
                "FROM work_task_answers WHERE answer_id LIKE 'ANS-%'",
                new MapSqlParameterSource(),
                Integer.class);
        int next = (max == null) ? 1 : max + 1;
        return "ANS-" + next;
    }

    private String findAnswerId(String workTaskId, String questionId) {
        List<String> ids = jdbc.queryForList("""
                SELECT answer_id FROM work_task_answers
                 WHERE work_task_id = :t AND question_id = :q
                 ORDER BY CAST(SUBSTRING(answer_id, 5) AS UNSIGNED) DESC
                 LIMIT 1
                """,
                new MapSqlParameterSource("t", workTaskId).addValue("q", questionId),
                String.class);
        return ids.isEmpty() ? null : ids.get(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static WorkTaskAnswer map(ResultSet rs, int rowNum) throws SQLException {
        return WorkTaskAnswer.builder()
                .answerId(rs.getString("answer_id"))
                .workTaskId(rs.getString("work_task_id"))
                .questionId(rs.getString("question_id"))
                .answerValue(rs.getString("answer_value"))
                .build();
    }
}
