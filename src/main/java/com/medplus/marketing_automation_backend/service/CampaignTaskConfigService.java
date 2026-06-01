package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.CampaignTaskConfigGroup;
import com.medplus.marketing_automation_backend.dto.CampaignTaskConfigRequest;
import com.medplus.marketing_automation_backend.repository.CampaignTaskConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class CampaignTaskConfigService {

    private final CampaignTaskConfigRepository repo;

    public CampaignTaskConfigService(CampaignTaskConfigRepository repo) {
        this.repo = repo;
    }

    /** Returns all configs grouped by their (campaignType, vertical, type, format) combination. */
    public List<CampaignTaskConfigGroup> listAllGrouped() {
        List<CampaignTaskConfigRepository.Row> flat = repo.findAllFlat();
        Map<String, CampaignTaskConfigGroup> groups = new LinkedHashMap<>();

        for (CampaignTaskConfigRepository.Row r : flat) {
            String key = r.campaignTypeId + "|" + r.businessVerticalId + "|"
                       + r.businessTypeId + "|" + r.storeFormatTypeId;

            groups.computeIfAbsent(key, k -> CampaignTaskConfigGroup.builder()
                .campaignTypeId(r.campaignTypeId)
                .campaignTypeName(r.campaignTypeName)
                .businessVerticalId(r.businessVerticalId)
                .businessVerticalName(r.businessVerticalName)
                .businessTypeId(r.businessTypeId)
                .businessTypeName(r.businessTypeName)
                .storeFormatTypeId(r.storeFormatTypeId)
                .storeFormatTypeName(r.storeFormatTypeName)
                .tasks(new ArrayList<>())
                .build()
            ).getTasks().add(CampaignTaskConfigGroup.TaskEntry.builder()
                .id(r.id)
                .taskId(r.granularTaskId)
                .taskName(r.taskName)
                .status(r.status)
                .build()
            );
        }
        return new ArrayList<>(groups.values());
    }

    /** Bulk-insert tasks for a combination (skips duplicates via INSERT IGNORE). */
    public void create(CampaignTaskConfigRequest req) {
        if (req.getTaskIds() == null || req.getTaskIds().isEmpty()) {
            throw new IllegalArgumentException("At least one task must be specified");
        }
        for (String taskId : req.getTaskIds()) {
            repo.insert(
                req.getCampaignTypeId(), req.getBusinessVerticalId(),
                req.getBusinessTypeId(), req.getStoreFormatTypeId(), taskId);
        }
    }

    /**
     * Replace all tasks for a combination with the new set.
     * Deletes existing rows for that combo, then inserts the new task list.
     */
    public void updateCombination(CampaignTaskConfigRequest req) {
        if (req.getTaskIds() == null || req.getTaskIds().isEmpty()) {
            throw new IllegalArgumentException("At least one task must be specified");
        }
        repo.deleteByCombination(
            req.getCampaignTypeId(), req.getBusinessVerticalId(),
            req.getBusinessTypeId(), req.getStoreFormatTypeId());
        for (String taskId : req.getTaskIds()) {
            repo.insert(
                req.getCampaignTypeId(), req.getBusinessVerticalId(),
                req.getBusinessTypeId(), req.getStoreFormatTypeId(), taskId);
        }
    }

    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    public void deleteByCombination(String campaignTypeId, String businessVerticalId,
                                    String businessTypeId, String storeFormatTypeId) {
        repo.deleteByCombination(campaignTypeId, businessVerticalId, businessTypeId, storeFormatTypeId);
    }
}
