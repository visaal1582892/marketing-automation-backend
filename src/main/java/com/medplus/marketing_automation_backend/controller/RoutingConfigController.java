package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.RoleTask;
import com.medplus.marketing_automation_backend.domain.RoutingRule;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.service.RoutingConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API for managing the routing engine configuration.
 *
 * Requirement → Role mappings  (which role handles which type of request)
 * <pre>
 *   GET    /api/master/routing/requirement-role              list all
 *   PUT    /api/master/routing/requirement-role/{reqTypeId}  set (upsert) mapping
 *   DELETE /api/master/routing/requirement-role/{mappingId}  remove mapping
 * </pre>
 *
 * Role → Task mappings  (which tasks a role is capable of executing)
 * <pre>
 *   GET    /api/master/routing/role-task                  list all
 *   GET    /api/master/routing/role-task/{roleId}         list tasks for a role
 *   POST   /api/master/routing/role-task                  add mapping
 *   DELETE /api/master/routing/role-task/{mappingId}      remove mapping
 * </pre>
 */
@RestController
@RequestMapping("/api/master/routing")
public class RoutingConfigController {

    private final RoutingConfigService service;

    public RoutingConfigController(RoutingConfigService service) {
        this.service = service;
    }

    // -------------------------------------------------------------------------
    // Public form helper: granular tasks for a given requirement type
    // -------------------------------------------------------------------------

    /**
     * Returns the list of granular tasks relevant to a requirement type.
     * Used by the Smart Form (Section 5) to populate the deliverables multi-select.
     * Accessible to any authenticated user.
     */
    @GetMapping("/tasks-for-requirement/{requirementTypeId}")
    @PreAuthorize("isAuthenticated()")
    public List<RoleTask> tasksForRequirement(@PathVariable String requirementTypeId) {
        return service.listTasksForRequirementType(requirementTypeId);
    }

    // -------------------------------------------------------------------------
    // Admin-only: Requirement → Role
    // -------------------------------------------------------------------------

    @GetMapping("/requirement-role")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoutingRule> listRequirementRoleMappings() {
        return service.listRequirementRoleMappings();
    }

    @PutMapping("/requirement-role/{requirementTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoutingRule setRequirementRoleMapping(
            @PathVariable String requirementTypeId,
            @RequestBody Map<String, String> body) {
        return service.setRequirementRoleMapping(requirementTypeId, body.get("roleId"));
    }

    @DeleteMapping("/requirement-role/{mappingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRequirementRoleMapping(@PathVariable int mappingId) {
        service.deleteRequirementRoleMapping(mappingId);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Admin-only: Role → Task
    // -------------------------------------------------------------------------

    /**
     * List role-task mappings.
     * Without page/size: returns full list (backward compatible).
     * With page/size: returns PagedResponse for the admin table.
     */
    @GetMapping("/role-task")
    @PreAuthorize("hasRole('ADMIN')")
    public Object listRoleTaskMappings(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        if (page != null || size != null) {
            return service.listRoleTaskMappingsPaged(roleName, taskName, status,
                                                      page != null ? page : 0,
                                                      size != null ? size : 20);
        }
        return service.listRoleTaskMappings();
    }

    @GetMapping("/role-task/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoleTask> listRoleTaskMappingsByRole(@PathVariable String roleId) {
        return service.listRoleTaskMappingsByRole(roleId);
    }

    @PostMapping("/role-task")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleTask> addRoleTaskMapping(@RequestBody Map<String, String> body) {
        return ResponseEntity.status(201)
                .body(service.addRoleTaskMapping(body.get("roleId"), body.get("taskId")));
    }

    @PatchMapping("/role-task/{mappingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public RoleTask updateRoleTaskMapping(
            @PathVariable int mappingId,
            @RequestBody Map<String, String> body) {
        RecordStatus status = RecordStatus.valueOf(body.get("status"));
        return service.updateRoleTaskMapping(mappingId, body.get("roleId"), body.get("taskId"), status);
    }

    @DeleteMapping("/role-task/{mappingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteRoleTaskMapping(@PathVariable int mappingId) {
        service.deleteRoleTaskMapping(mappingId);
        return ResponseEntity.noContent().build();
    }
}
