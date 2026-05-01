package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.RoleTask;
import com.medplus.marketing_automation_backend.domain.RoutingRule;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.RoutingConfigRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoutingConfigService {

    private final RoutingConfigRepository repo;

    public RoutingConfigService(RoutingConfigRepository repo) {
        this.repo = repo;
    }

    // -------------------------------------------------------------------------
    // Requirement → Role
    // -------------------------------------------------------------------------

    public List<RoutingRule> listRequirementRoleMappings() {
        return repo.findAllRequirementRoleMappings();
    }

    public RoutingRule setRequirementRoleMapping(String requirementTypeId, String roleId) {
        if (requirementTypeId == null || requirementTypeId.isBlank()) {
            throw new BadRequestException("requirementTypeId is required");
        }
        if (roleId == null || roleId.isBlank()) {
            throw new BadRequestException("roleId is required");
        }
        repo.upsertRequirementRoleMapping(requirementTypeId, roleId);
        return repo.findAllRequirementRoleMappings().stream()
                .filter(r -> r.getRequirementTypeId().equals(requirementTypeId))
                .findFirst()
                .orElseThrow();
    }

    public void deleteRequirementRoleMapping(int mappingId) {
        if (repo.deleteRequirementRoleMapping(mappingId) == 0) {
            throw new ResourceNotFoundException("Routing rule " + mappingId + " not found");
        }
    }

    // -------------------------------------------------------------------------
    // Role → Task
    // -------------------------------------------------------------------------

    /**
     * Returns granular tasks relevant to a requirement type, by resolving
     * requirement → default role → role-task mappings.
     * Used by the Smart Form to populate the deliverables multi-select.
     */
    public List<RoleTask> listTasksForRequirementType(String requirementTypeId) {
        return repo.findDefaultRoleForRequirement(requirementTypeId)
                .map(roleId -> repo.findRoleTaskMappingsByRole(roleId))
                .orElse(java.util.Collections.emptyList());
    }

    public List<RoleTask> listRoleTaskMappings() {
        return repo.findAllRoleTaskMappings();
    }

    public List<RoleTask> listRoleTaskMappingsByRole(String roleId) {
        return repo.findRoleTaskMappingsByRole(roleId);
    }

    public RoleTask addRoleTaskMapping(String roleId, String taskId) {
        if (roleId == null || roleId.isBlank()) throw new BadRequestException("roleId is required");
        if (taskId == null || taskId.isBlank()) throw new BadRequestException("taskId is required");
        repo.addRoleTaskMapping(roleId, taskId);
        return repo.findRoleTaskMappingsByRole(roleId).stream()
                .filter(rt -> rt.getTaskId().equals(taskId))
                .findFirst()
                .orElseThrow();
    }

    public RoleTask updateRoleTaskMapping(int mappingId, RecordStatus status) {
        if (status == null) throw new BadRequestException("status is required");
        if (repo.updateRoleTaskStatus(mappingId, status) == 0) {
            throw new ResourceNotFoundException("Role-task mapping " + mappingId + " not found");
        }
        return repo.findRoleTaskMappingById(mappingId).orElseThrow();
    }

    public void deleteRoleTaskMapping(int mappingId) {
        if (repo.deleteRoleTaskMapping(mappingId) == 0) {
            throw new ResourceNotFoundException("Role-task mapping " + mappingId + " not found");
        }
    }
}
