package com.medplus.marketing_automation_backend.repository;

import com.medplus.marketing_automation_backend.domain.CampaignTaskConfigGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Slf4j
@Repository
public class CampaignTaskConfigRepository {

    private final JdbcTemplate jdbc;

    public CampaignTaskConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT_ALL =
        "SELECT ctc.id, " +
        "  ctc.campaign_type_id,     ct.campaign_type_name, " +
        "  ctc.business_vertical_id, bv.business_vertical_name, " +
        "  ctc.business_type_id,     bt.business_type_name, " +
        "  ctc.store_format_type_id, sft.store_format_type_name, " +
        "  ctc.granular_task_id,     gt.task_name, ctc.status " +
        "FROM campaign_task_config ctc " +
        "LEFT JOIN campaign_types     ct  ON ctc.campaign_type_id     != '' AND ct.campaign_type_id     = ctc.campaign_type_id " +
        "LEFT JOIN business_verticals bv  ON ctc.business_vertical_id != '' AND bv.business_vertical_id = ctc.business_vertical_id " +
        "LEFT JOIN business_types     bt  ON ctc.business_type_id     != '' AND bt.business_type_id     = ctc.business_type_id " +
        "LEFT JOIN store_format_types sft ON ctc.store_format_type_id != '' AND sft.store_format_type_id = ctc.store_format_type_id " +
        "LEFT JOIN granular_tasks     gt  ON gt.task_id = ctc.granular_task_id " +
        "ORDER BY ctc.campaign_type_id, ctc.business_vertical_id, ctc.business_type_id, ctc.store_format_type_id, gt.task_name";

    /** Flat list — service groups these into CampaignTaskConfigGroup objects. */
    public List<Row> findAllFlat() {
        return jdbc.query(SELECT_ALL, this::mapRow);
    }

    public int insert(String campaignTypeId, String businessVerticalId,
                      String businessTypeId, String storeFormatTypeId, String taskId) {
        return jdbc.update(
            "INSERT IGNORE INTO campaign_task_config " +
            "(campaign_type_id, business_vertical_id, business_type_id, store_format_type_id, granular_task_id) " +
            "VALUES (?,?,?,?,?)",
            safe(campaignTypeId), safe(businessVerticalId), safe(businessTypeId), safe(storeFormatTypeId), taskId);
    }

    public int deleteById(Long id) {
        return jdbc.update("DELETE FROM campaign_task_config WHERE id = ?", id);
    }

    public int deleteByCombination(String campaignTypeId, String businessVerticalId,
                                   String businessTypeId, String storeFormatTypeId) {
        return jdbc.update(
            "DELETE FROM campaign_task_config " +
            "WHERE campaign_type_id = ? AND business_vertical_id = ? AND business_type_id = ? AND store_format_type_id = ?",
            safe(campaignTypeId), safe(businessVerticalId), safe(businessTypeId), safe(storeFormatTypeId));
    }

    private String safe(String v) { return v == null ? "" : v.trim(); }

    private Row mapRow(ResultSet rs, int i) throws SQLException {
        Row r = new Row();
        r.id                  = rs.getLong("id");
        r.campaignTypeId      = rs.getString("campaign_type_id");
        r.campaignTypeName    = rs.getString("campaign_type_name");
        r.businessVerticalId  = rs.getString("business_vertical_id");
        r.businessVerticalName = rs.getString("business_vertical_name");
        r.businessTypeId      = rs.getString("business_type_id");
        r.businessTypeName    = rs.getString("business_type_name");
        r.storeFormatTypeId   = rs.getString("store_format_type_id");
        r.storeFormatTypeName = rs.getString("store_format_type_name");
        r.granularTaskId      = rs.getString("granular_task_id");
        r.taskName            = rs.getString("task_name");
        r.status              = rs.getString("status");
        return r;
    }

    /** Internal flat row — converted to grouped domain by the service. */
    public static class Row {
        public Long   id;
        public String campaignTypeId;
        public String campaignTypeName;
        public String businessVerticalId;
        public String businessVerticalName;
        public String businessTypeId;
        public String businessTypeName;
        public String storeFormatTypeId;
        public String storeFormatTypeName;
        public String granularTaskId;
        public String taskName;
        public String status;
    }
}
