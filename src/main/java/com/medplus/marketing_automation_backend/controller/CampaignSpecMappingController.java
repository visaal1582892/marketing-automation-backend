package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.CampaignSpecMapping;
import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.service.CampaignSpecMappingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for Campaign Specifications hierarchical mappings.
 *
 * <pre>
 *   GET  /api/campaign-specs/vertical-type-mappings                    list all BV → BT mappings
 *   GET  /api/campaign-specs/business-types?verticalId={id}            BT options for a BV (form use)
 *   POST /api/campaign-specs/vertical-type-mappings                    create mapping (admin)
 *   DELETE /api/campaign-specs/vertical-type-mappings/{id}             delete mapping (admin)
 *
 *   GET  /api/campaign-specs/type-format-mappings                      list all BT → SFT mappings
 *   GET  /api/campaign-specs/store-formats?businessTypeId={id}         SFT options for a BT (form use)
 *   POST /api/campaign-specs/type-format-mappings                      create mapping (admin)
 *   DELETE /api/campaign-specs/type-format-mappings/{id}               delete mapping (admin)
 * </pre>
 */
@RestController
@RequestMapping("/api/campaign-specs")
public class CampaignSpecMappingController {

    private final CampaignSpecMappingService service;

    public CampaignSpecMappingController(CampaignSpecMappingService service) {
        this.service = service;
    }

    // ── Business Vertical → Business Type ────────────────────────────────────

    @GetMapping("/vertical-type-mappings")
    @PreAuthorize("isAuthenticated()")
    public List<CampaignSpecMapping> listVerticalTypeMappings() {
        return service.listAllVerticalTypeMappings();
    }

    /** Used by the campaign form to load Business Type options after a vertical is chosen. */
    @GetMapping("/business-types")
    @PreAuthorize("isAuthenticated()")
    public List<MasterItem> getBusinessTypesByVertical(
            @RequestParam String verticalId) {
        return service.getBusinessTypesByVertical(verticalId);
    }

    @PostMapping("/vertical-type-mappings")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<CampaignSpecMapping> createVerticalTypeMapping(
            @RequestBody Map<String, String> body) {
        CampaignSpecMapping mapping = service.createVerticalTypeMapping(
                body.get("verticalId"), body.get("typeId"));
        return ResponseEntity.status(201).body(mapping);
    }

    @DeleteMapping("/vertical-type-mappings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> deleteVerticalTypeMapping(@PathVariable Integer id) {
        service.deleteVerticalTypeMapping(id);
        return ResponseEntity.noContent().build();
    }

    // ── Business Type → Store Format Type ────────────────────────────────────

    @GetMapping("/type-format-mappings")
    @PreAuthorize("isAuthenticated()")
    public List<CampaignSpecMapping> listTypeFormatMappings() {
        return service.listAllTypeFormatMappings();
    }

    /** Used by the campaign form to load Store Format options after a business type is chosen. */
    @GetMapping("/store-formats")
    @PreAuthorize("isAuthenticated()")
    public List<MasterItem> getStoreFormatsByBusinessType(
            @RequestParam String businessTypeId) {
        return service.getStoreFormatsByBusinessType(businessTypeId);
    }

    @PostMapping("/type-format-mappings")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<CampaignSpecMapping> createTypeFormatMapping(
            @RequestBody Map<String, String> body) {
        CampaignSpecMapping mapping = service.createTypeFormatMapping(
                body.get("typeId"), body.get("formatId"));
        return ResponseEntity.status(201).body(mapping);
    }

    @DeleteMapping("/type-format-mappings/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> deleteTypeFormatMapping(@PathVariable Integer id) {
        service.deleteTypeFormatMapping(id);
        return ResponseEntity.noContent().build();
    }
}
