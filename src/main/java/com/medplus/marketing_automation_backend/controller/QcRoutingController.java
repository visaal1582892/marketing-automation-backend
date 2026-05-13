package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.QcRouting;
import com.medplus.marketing_automation_backend.service.QcRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only API for the QC Routing configuration.
 *
 * GET  /api/admin/qc-routing  — returns current mappings + available roles
 * PUT  /api/admin/qc-routing  — replaces the entire routing config
 */
@RestController
@RequestMapping("/api/admin/qc-routing")
@PreAuthorize("hasRole('ADMIN')")
public class QcRoutingController {

    private final QcRoutingService service;

    public QcRoutingController(QcRoutingService service) {
        this.service = service;
    }

    /**
     * Returns the full routing config together with the available role lists so
     * the frontend can render the checkbox matrix in a single request.
     */
    @GetMapping
    public Map<String, Object> getConfig() {
        return Map.of(
                "mappings",     service.findAll(),
                "workerRoles",  service.findWorkerRoles(),
                "managerRoles", service.findManagerRoles()
        );
    }

    /**
     * Replaces the entire QC routing config.
     * Body: [ { "workerRoleId": "5", "managerRoleId": "13" }, … ]
     */
    @PutMapping
    public ResponseEntity<Void> saveConfig(@RequestBody List<QcRouting> mappings) {
        service.replaceAll(mappings);
        return ResponseEntity.noContent().build();
    }
}
