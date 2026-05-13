package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.QcRouting;
import com.medplus.marketing_automation_backend.repository.QcRoutingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Manager role names that have access to manager tools (QC review). */
@Service
public class QcRoutingService {

    /** Role names that have manager-tool access (i.e. appear as reviewers). */
    static final List<String> MANAGER_ROLE_NAMES = List.of("Marketing Manager", "Procurement Manager");

    /** Role names excluded from the worker-role list on the config page. */
    static final List<String> NON_WORKER_ROLE_NAMES = List.of(
            "Admin", "Requestor", "Marketing Manager", "Procurement Manager",
            "Head", "Regional Manager");

    private final QcRoutingRepository repo;

    public QcRoutingService(QcRoutingRepository repo) {
        this.repo = repo;
    }

    /** All current QC routing mappings. */
    @Transactional(readOnly = true)
    public List<QcRouting> findAll() {
        return repo.findAll();
    }

    /**
     * Worker roles that are mapped to at least one of the given manager role IDs.
     * Returns an empty list when there are no mappings at all for those manager roles —
     * the caller treats empty as "show all QC tasks" (backwards-compatible default).
     */
    @Transactional(readOnly = true)
    public List<String> findWorkerRolesForManagerRoles(List<String> managerRoleIds) {
        return repo.findWorkerRolesForManagerRoles(managerRoleIds);
    }

    /** All worker roles (non-manager, non-admin) for the config matrix. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findWorkerRoles() {
        return repo.findRolesExcluding(NON_WORKER_ROLE_NAMES);
    }

    /** Manager roles (those with manager-tool access) for the config matrix. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> findManagerRoles() {
        return repo.findRolesByNames(MANAGER_ROLE_NAMES);
    }

    /** Replaces the entire config with the provided list. */
    @Transactional
    public void replaceAll(List<QcRouting> mappings) {
        repo.replaceAll(mappings);
    }
}
