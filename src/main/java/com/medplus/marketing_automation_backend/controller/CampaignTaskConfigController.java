package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.CampaignTaskConfigGroup;
import com.medplus.marketing_automation_backend.dto.CampaignTaskConfigRequest;
import com.medplus.marketing_automation_backend.service.CampaignTaskConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for Campaign Task Configurations.
 *
 * <pre>
 *   GET    /api/campaign-task-config                        list all (grouped by combination)
 *   POST   /api/campaign-task-config                        bulk-create tasks for a combination
 *   PUT    /api/campaign-task-config/combination            replace tasks for a combination
 *   DELETE /api/campaign-task-config/{id}                   delete a single task row
 *   DELETE /api/campaign-task-config/combination            delete all rows for a combination
 * </pre>
 */
@RestController
@RequestMapping("/api/campaign-task-config")
public class CampaignTaskConfigController {

    private final CampaignTaskConfigService service;

    public CampaignTaskConfigController(CampaignTaskConfigService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CampaignTaskConfigGroup> listAll() {
        return service.listAllGrouped();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> create(@RequestBody CampaignTaskConfigRequest req) {
        service.create(req);
        return ResponseEntity.status(201).build();
    }

    @PutMapping("/combination")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> updateCombination(@RequestBody CampaignTaskConfigRequest req) {
        service.updateCombination(req);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        service.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/combination")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> deleteByCombination(
            @RequestParam(required = false, defaultValue = "") String campaignTypeId,
            @RequestParam(required = false, defaultValue = "") String businessVerticalId,
            @RequestParam(required = false, defaultValue = "") String businessTypeId,
            @RequestParam(required = false, defaultValue = "") String storeFormatTypeId) {
        service.deleteByCombination(campaignTypeId, businessVerticalId, businessTypeId, storeFormatTypeId);
        return ResponseEntity.noContent().build();
    }
}
