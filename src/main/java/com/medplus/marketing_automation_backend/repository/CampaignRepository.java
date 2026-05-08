package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.Campaign;
import com.medplus.marketing_automation_backend.domain.CampaignDeliverable;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.enums.CampaignStatus;
import com.medplus.marketing_automation_backend.enums.Priority;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class CampaignRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public CampaignRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_BASE = """
            SELECT c.campaign_id, c.requestor_id, c.department_id, c.target_location,
                   c.business_objective,
                   c.task_type_id,
                   c.audience_type_id, c.language,
                   c.has_offer, c.offer_type_id, c.key_message, c.supporting_proof,
                   c.tone, c.priority,
                   c.budget_tier, c.vendor_required, c.vendor_type,
                   c.kpi_type, c.expected_output,
                   c.status, c.routing_notes,
                   c.flagged_inconsistency, c.inconsistency_reason,
                   c.rejection_reason,
                   c.created_at, c.updated_at,
                   u.full_name   AS requestor_name,
                   d.department_name,
                   COALESCE(ot.offer_type_name,   c.offer_type_id)                AS offer_type_name_resolved,
                   COALESCE(sp.supporting_proof_name, c.supporting_proof)         AS supporting_proof_resolved,
                   COALESCE(btr.budget_tier_name, c.budget_tier)                  AS budget_tier_resolved,
                   COALESCE(kt.kpi_type_name,     c.kpi_type)                     AS kpi_type_resolved,
                   COALESCE(eo.expected_output_name, c.expected_output)           AS expected_output_resolved,
                   COALESCE(bo.business_objective_name, c.business_objective)     AS business_objective_resolved,
                   (SELECT COUNT(*) FROM work_tasks wt WHERE wt.campaign_id = c.campaign_id AND wt.status <> 'CANCELLED') AS task_count,
                   (SELECT COUNT(*) FROM work_tasks wt WHERE wt.campaign_id = c.campaign_id AND wt.status = 'COMPLETED')  AS completed_task_count,
                   (SELECT COUNT(*) > 0 FROM work_tasks wt WHERE wt.campaign_id = c.campaign_id AND wt.status = 'REWORK')    AS has_rework,
                   (SELECT COUNT(*) > 0 FROM work_tasks wt WHERE wt.campaign_id = c.campaign_id AND wt.status = 'QC_REVIEW') AS has_qc_review
            FROM campaigns c
            LEFT JOIN users               u   ON u.user_id   = c.requestor_id
            LEFT JOIN departments         d   ON d.department_id = c.department_id
            LEFT JOIN offer_types         ot  ON ot.offer_type_id         = c.offer_type_id
            LEFT JOIN supporting_proofs   sp  ON sp.supporting_proof_id   = c.supporting_proof
            LEFT JOIN budget_tiers        btr ON btr.budget_tier_id       = c.budget_tier
            LEFT JOIN kpi_types           kt  ON kt.kpi_type_id           = c.kpi_type
            LEFT JOIN expected_outputs    eo  ON eo.expected_output_id    = c.expected_output
            LEFT JOIN business_objectives bo  ON bo.business_objective_id = c.business_objective
            """;

    // -------------------------------------------------------------------------
    // Campaign Write operations
    // -------------------------------------------------------------------------

    public Integer insert(Campaign c) {
        String sql = """
                INSERT INTO campaigns (
                    requestor_id, department_id, target_location, business_objective,
                    task_type_id, audience_type_id, language,
                    has_offer, offer_type_id, key_message, supporting_proof,
                    tone, priority,
                    budget_tier, vendor_required, vendor_type,
                    kpi_type, expected_output, status,
                    flagged_inconsistency, inconsistency_reason
                ) VALUES (
                    :requestorId, :departmentId, :targetLocation, :businessObjective,
                    :taskTypeId, :audienceTypeId, :language,
                    :hasOffer, :offerTypeId, :keyMessage, :supportingProof,
                    :tone, :priority,
                    :budgetTier, :vendorRequired, :vendorType,
                    :kpiType, :expectedOutput, :status,
                    :flaggedInconsistency, :inconsistencyReason
                )
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("requestorId",          c.getRequestorId())
                .addValue("departmentId",         c.getDepartmentId())
                .addValue("targetLocation",       c.getTargetLocation())
                .addValue("businessObjective",    c.getBusinessObjective())
                .addValue("taskTypeId",           c.getTaskTypeId())
                .addValue("audienceTypeId",       c.getAudienceTypeId())
                .addValue("language",             c.getLanguage())
                .addValue("hasOffer",             c.getHasOffer() == null ? "NO" : c.getHasOffer())
                .addValue("offerTypeId",          c.getOfferTypeId())
                .addValue("keyMessage",           c.getKeyMessage())
                .addValue("supportingProof",      c.getSupportingProof())
                .addValue("tone",                 c.getTone())
                .addValue("priority",             c.getPriority() == null ? "MEDIUM" : c.getPriority().name())
                .addValue("budgetTier",           c.getBudgetTier())
                .addValue("vendorRequired",       c.getVendorRequired() == null ? "NO" : c.getVendorRequired())
                .addValue("vendorType",           c.getVendorType())
                .addValue("kpiType",              c.getKpiType())
                .addValue("expectedOutput",       c.getExpectedOutput())
                .addValue("status",               c.getStatus() == null ? "IN_PROGRESS" : c.getStatus().name())
                .addValue("flaggedInconsistency", Boolean.TRUE.equals(c.getFlaggedInconsistency()) ? 1 : 0)
                .addValue("inconsistencyReason",  c.getInconsistencyReason());

        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(sql, p, kh, new String[]{"campaign_id"});
        return kh.getKey() == null ? null : kh.getKey().intValue();
    }

    public int updateStatus(int campaignId, CampaignStatus status) {
        return jdbc.update(
                "UPDATE campaigns SET status = :status WHERE campaign_id = :id",
                new MapSqlParameterSource("status", status.name()).addValue("id", campaignId));
    }

    /**
     * Marketing Head adjustment of a campaign's priority.
     * The inconsistency flag is recomputed in the same statement so any HIGH +
     * low-budget combo gets re-flagged (or cleared) without a second roundtrip.
     */
    public int updatePriority(int campaignId, Priority newPriority,
                               boolean flagged, String inconsistencyReason) {
        return jdbc.update("""
                UPDATE campaigns
                   SET priority               = :priority,
                       flagged_inconsistency  = :flagged,
                       inconsistency_reason   = :reason
                 WHERE campaign_id = :id
                """,
                new MapSqlParameterSource("priority", newPriority.name())
                        .addValue("flagged",  flagged)
                        .addValue("reason",   inconsistencyReason)
                        .addValue("id",       campaignId));
    }

    /**
     * Marketing Head / Admin editable fields on a non-terminal campaign.
     * Null values leave the existing column unchanged (COALESCE pattern).
     */
    public int updateCampaignFields(int campaignId, Priority priority, String keyMessage,
                                     String budgetTier, boolean flagged, String inconsistencyReason) {
        return jdbc.update("""
                UPDATE campaigns
                   SET priority              = COALESCE(:priority, priority),
                       key_message           = COALESCE(:keyMessage, key_message),
                       budget_tier           = COALESCE(:budgetTier, budget_tier),
                       flagged_inconsistency = :flagged,
                       inconsistency_reason  = :reason
                 WHERE campaign_id = :id
                """,
                new MapSqlParameterSource("priority",   priority == null ? null : priority.name())
                        .addValue("keyMessage", keyMessage)
                        .addValue("budgetTier", budgetTier)
                        .addValue("flagged",    flagged)
                        .addValue("reason",     inconsistencyReason)
                        .addValue("id",         campaignId));
    }

    public int updateRoutingNotes(int campaignId, String notes) {
        return jdbc.update(
                "UPDATE campaigns SET routing_notes = :notes WHERE campaign_id = :id",
                new MapSqlParameterSource("notes", notes).addValue("id", campaignId));
    }

    public int updateStatusAndNotes(int campaignId, CampaignStatus status, String notes) {
        return jdbc.update(
                "UPDATE campaigns SET status = :status, routing_notes = :notes WHERE campaign_id = :id",
                new MapSqlParameterSource("status", status.name())
                        .addValue("notes", notes)
                        .addValue("id", campaignId));
    }

    public int delete(int campaignId) {
        return jdbc.update(
                "DELETE FROM campaigns WHERE campaign_id = :id",
                new MapSqlParameterSource("id", campaignId));
    }

    // -------------------------------------------------------------------------
    // Campaign Read operations
    // -------------------------------------------------------------------------

    public Optional<Campaign> findById(int id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    SELECT_BASE + " WHERE c.campaign_id = :id",
                    new MapSqlParameterSource("id", id),
                    CampaignRepository::map));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public List<Campaign> findAll(boolean includeInactive) {
        return jdbc.query(SELECT_BASE + " ORDER BY c.campaign_id DESC",
                CampaignRepository::map);
    }

    public List<Campaign> findByRequestorId(int requestorId) {
        return jdbc.query(SELECT_BASE + " WHERE c.requestor_id = :id ORDER BY c.campaign_id DESC",
                new MapSqlParameterSource("id", requestorId),
                CampaignRepository::map);
    }

    public List<Campaign> findByStatus(CampaignStatus status) {
        return jdbc.query(SELECT_BASE + " WHERE c.status = :status ORDER BY c.campaign_id DESC",
                new MapSqlParameterSource("status", status.name()),
                CampaignRepository::map);
    }

    /**
     * Full update of all requestor-editable campaign fields.
     * Used when a requestor edits their own campaign.
     */
    public int updateRequestorFields(int campaignId,
                                     String departmentId, String targetLocation,
                                     String businessObjective,
                                     String taskTypeId,
                                     String audienceTypeId, String language,
                                     String hasOffer, String offerTypeId,
                                     String keyMessage, String supportingProof,
                                     String tone, Priority priority,
                                     String budgetTier, String vendorRequired,
                                     String vendorType, String kpiType,
                                     String expectedOutput,
                                     boolean flagged, String inconsistencyReason) {
        return jdbc.update("""
                UPDATE campaigns
                   SET department_id         = :deptId,
                       target_location       = :targetLocation,
                       business_objective    = :businessObjective,
                       task_type_id          = :taskTypeId,
                       audience_type_id      = :audienceTypeId,
                       language              = :language,
                       has_offer             = :hasOffer,
                       offer_type_id         = :offerTypeId,
                       key_message           = :keyMessage,
                       supporting_proof      = :supportingProof,
                       tone                  = :tone,
                       priority              = :priority,
                       budget_tier           = :budgetTier,
                       vendor_required       = :vendorRequired,
                       vendor_type           = :vendorType,
                       kpi_type              = :kpiType,
                       expected_output       = :expectedOutput,
                       flagged_inconsistency = :flagged,
                       inconsistency_reason  = :reason
                 WHERE campaign_id = :id
                """,
                new MapSqlParameterSource()
                        .addValue("deptId",             departmentId)
                        .addValue("targetLocation",     targetLocation)
                        .addValue("businessObjective",  businessObjective)
                        .addValue("taskTypeId",         taskTypeId)
                        .addValue("audienceTypeId",     audienceTypeId)
                        .addValue("language",           language)
                        .addValue("hasOffer",           hasOffer)
                        .addValue("offerTypeId",        offerTypeId)
                        .addValue("keyMessage",         keyMessage)
                        .addValue("supportingProof",    supportingProof)
                        .addValue("tone",               tone)
                        .addValue("priority",           priority == null ? null : priority.name())
                        .addValue("budgetTier",         budgetTier)
                        .addValue("vendorRequired",     vendorRequired)
                        .addValue("vendorType",         vendorType)
                        .addValue("kpiType",            kpiType)
                        .addValue("expectedOutput",     expectedOutput)
                        .addValue("flagged",            flagged)
                        .addValue("reason",             inconsistencyReason)
                        .addValue("id",                 campaignId));
    }

    // -------------------------------------------------------------------------
    // Campaign Deliverables (per-task specs from Smart Form)
    // -------------------------------------------------------------------------

    public void insertDeliverable(int campaignId, String granularTaskId) {
        jdbc.update("""
                INSERT IGNORE INTO campaign_deliverables
                    (campaign_id, granular_task_id)
                VALUES (:campaignId, :taskId)
                """,
                new MapSqlParameterSource()
                        .addValue("campaignId",  campaignId)
                        .addValue("taskId",      granularTaskId));
    }

    public List<CampaignDeliverable> findDeliverablesByCampaignId(int campaignId) {
        return jdbc.query("""
                SELECT cd.spec_id, cd.campaign_id, cd.granular_task_id,
                       gt.task_name AS granular_task_name
                FROM   campaign_deliverables cd
                LEFT JOIN granular_tasks gt ON gt.task_id = cd.granular_task_id
                WHERE  cd.campaign_id = :id
                ORDER  BY cd.spec_id
                """,
                new MapSqlParameterSource("id", campaignId),
                (rs, n) -> CampaignDeliverable.builder()
                        .specId(rs.getInt("spec_id"))
                        .campaignId(rs.getInt("campaign_id"))
                        .granularTaskId(rs.getString("granular_task_id"))
                        .granularTaskName(rs.getString("granular_task_name"))
                        .build());
    }

    public Optional<CampaignDeliverable> findDeliverableBySpecId(int specId) {
        List<CampaignDeliverable> rows = jdbc.query("""
                SELECT cd.spec_id, cd.campaign_id, cd.granular_task_id,
                       gt.task_name AS granular_task_name
                FROM   campaign_deliverables cd
                LEFT JOIN granular_tasks gt ON gt.task_id = cd.granular_task_id
                WHERE  cd.spec_id = :specId
                """,
                new MapSqlParameterSource("specId", specId),
                (rs, n) -> CampaignDeliverable.builder()
                        .specId(rs.getInt("spec_id"))
                        .campaignId(rs.getInt("campaign_id"))
                        .granularTaskId(rs.getString("granular_task_id"))
                        .granularTaskName(rs.getString("granular_task_name"))
                        .build());
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public int deleteDeliverableBySpecId(int specId) {
        return jdbc.update(
                "DELETE FROM campaign_deliverables WHERE spec_id = :specId",
                new MapSqlParameterSource("specId", specId));
    }

    // -------------------------------------------------------------------------
    // Campaign Files
    // -------------------------------------------------------------------------

    public void insertCampaignFile(int campaignId, String fileUrl, String fileName) {
        jdbc.update("""
                INSERT INTO campaign_files (campaign_id, file_url, file_name)
                VALUES (:campaignId, :fileUrl, :fileName)
                """,
                new MapSqlParameterSource()
                        .addValue("campaignId", campaignId)
                        .addValue("fileUrl",    fileUrl)
                        .addValue("fileName",   fileName));
    }

    public List<String> findFileUrlsByCampaignId(int campaignId) {
        return jdbc.queryForList(
                "SELECT file_url FROM campaign_files WHERE campaign_id = :id ORDER BY file_id",
                new MapSqlParameterSource("id", campaignId),
                String.class);
    }

    public List<String> findFileNamesByCampaignId(int campaignId) {
        return jdbc.queryForList(
                "SELECT file_name FROM campaign_files WHERE campaign_id = :id ORDER BY file_id",
                new MapSqlParameterSource("id", campaignId),
                String.class);
    }

    public void deleteFileByUrl(int campaignId, String fileUrl) {
        jdbc.update(
                "DELETE FROM campaign_files WHERE campaign_id = :campaignId AND file_url = :fileUrl",
                new MapSqlParameterSource()
                        .addValue("campaignId", campaignId)
                        .addValue("fileUrl",    fileUrl));
    }

    // -------------------------------------------------------------------------
    // Paged / filtered queries
    // -------------------------------------------------------------------------

    /**
     * Paginated campaign list scoped to a single requestor, with optional
     * column-level filters on campaignId, status, priority, and date range.
     * {@code taskTypeName} filtering is handled post-query (service layer) because
     * task_type_id is stored as a JSON array and requires application-side resolution.
     */
    public PagedResponse<Campaign> findByRequestorIdPaged(
            int requestorId,
            String campaignId, String status, String priority,
            LocalDate dateFrom, LocalDate dateTo,
            int page, int size) {

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("requestorId", requestorId);
        List<String> conds = new ArrayList<>();
        conds.add("c.requestor_id = :requestorId");

        if (campaignId != null && !campaignId.isBlank()) {
            conds.add("CAST(c.campaign_id AS CHAR) LIKE :campaignId");
            params.addValue("campaignId", "%" + campaignId.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            conds.add("c.status = :status");
            params.addValue("status", status.trim());
        }
        if (priority != null && !priority.isBlank()) {
            conds.add("c.priority = :priority");
            params.addValue("priority", priority.trim());
        }
        if (dateFrom != null) {
            conds.add("c.created_at >= :dateFrom");
            params.addValue("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conds.add("c.created_at <= :dateTo");
            params.addValue("dateTo", dateTo.atTime(23, 59, 59));
        }

        String where = " WHERE " + String.join(" AND ", conds);
        String countSql = "SELECT COUNT(*) FROM campaigns c " + where;
        Long total = jdbc.queryForObject(countSql, params, Long.class);
        if (total == null) total = 0L;

        params.addValue("_size", size).addValue("_offset", (long) page * size);
        List<Campaign> content = jdbc.query(
                SELECT_BASE + where + " ORDER BY c.campaign_id DESC LIMIT :_size OFFSET :_offset",
                params, CampaignRepository::map);

        return PagedResponse.of(content, total, page, size);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Campaign map(ResultSet rs, int rowNum) throws SQLException {
        String rawOfferTypeId = rs.getString("offer_type_id");
        return Campaign.builder()
                .campaignId(rs.getInt("campaign_id"))
                .requestorId(rs.getInt("requestor_id"))
                .requestorName(rs.getString("requestor_name"))
                .departmentId(rs.getString("department_id"))
                .departmentName(rs.getString("department_name"))
                .targetLocation(rs.getString("target_location"))
                .businessObjectiveId(rs.getString("business_objective"))
                .businessObjective(rs.getString("business_objective_resolved"))
                .taskTypeId(rs.getString("task_type_id"))
                .audienceTypeId(rs.getString("audience_type_id"))
                .language(rs.getString("language"))
                .hasOffer(rs.getString("has_offer"))
                .offerTypeId(rawOfferTypeId)
                .offerTypeName(rs.getString("offer_type_name_resolved"))
                .keyMessage(rs.getString("key_message"))
                .supportingProofId(rs.getString("supporting_proof"))
                .supportingProof(rs.getString("supporting_proof_resolved"))
                .tone(rs.getString("tone"))
                .priority(safeEnum(Priority.class, rs.getString("priority")))
                .budgetTierId(rs.getString("budget_tier"))
                .budgetTier(rs.getString("budget_tier_resolved"))
                .vendorRequired(rs.getString("vendor_required"))
                .vendorType(rs.getString("vendor_type"))
                .kpiTypeId(rs.getString("kpi_type"))
                .kpiType(rs.getString("kpi_type_resolved"))
                .expectedOutputId(rs.getString("expected_output"))
                .expectedOutput(rs.getString("expected_output_resolved"))
                .status(safeEnum(CampaignStatus.class, rs.getString("status")))
                .routingNotes(rs.getString("routing_notes"))
                .flaggedInconsistency(readBoolean(rs, "flagged_inconsistency"))
                .inconsistencyReason(rs.getString("inconsistency_reason"))
                .rejectionReason(rs.getString("rejection_reason"))
                .createdAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime())
                .updatedAt(rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime())
                .taskCount(readNullableInt(rs, "task_count"))
                .completedTaskCount(readNullableInt(rs, "completed_task_count"))
                .hasRework(readBoolean(rs, "has_rework"))
                .hasQcReview(readBoolean(rs, "has_qc_review"))
                .build();
    }

    private static Integer readNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static Boolean readBoolean(ResultSet rs, String col) throws SQLException {
        try {
            int v = rs.getInt(col);
            return rs.wasNull() ? null : v != 0;
        } catch (SQLException e) {
            return null;
        }
    }

    private static <E extends Enum<E>> E safeEnum(Class<E> clazz, String value) {
        if (value == null || value.isBlank()) return null;
        try { return Enum.valueOf(clazz, value); }
        catch (IllegalArgumentException e) { return null; }
    }
}
