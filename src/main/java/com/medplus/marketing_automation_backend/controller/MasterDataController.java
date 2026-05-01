package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.enums.MasterTableType;
import com.medplus.marketing_automation_backend.service.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Single REST controller exposing CRUD for every master table.
 *
 * <pre>
 *   GET    /api/master/{resource}                       list active records
 *   GET    /api/master/{resource}?includeInactive=true  list all records
 *   GET    /api/master/{resource}/{id}                  fetch one (e.g. ROLE-3)
 *   POST   /api/master/{resource}                       create
 *   PUT    /api/master/{resource}/{id}                  update name + status
 *   DELETE /api/master/{resource}/{id}                  hard-delete
 *   GET    /api/master/_resources                       list available slugs
 * </pre>
 */
@RestController
@RequestMapping("/api/master")
public class MasterDataController {

    private final MasterDataService service;

    public MasterDataController(MasterDataService service) {
        this.service = service;
    }

    /** Available resource slugs — any authenticated user can read (used by form dropdowns). */
    @GetMapping("/_resources")
    @PreAuthorize("isAuthenticated()")
    public List<Map<String, String>> resources() {
        return Arrays.stream(MasterTableType.values())
                .map(t -> Map.of(
                        "slug",  t.pathSlug().substring(1),
                        "table", t.tableName()))
                .toList();
    }

    /** List master items — any authenticated user can read (required for form dropdowns). */
    @GetMapping("/{resource}")
    @PreAuthorize("isAuthenticated()")
    public List<MasterItem> list(@PathVariable String resource,
                                 @RequestParam(name = "includeInactive", required = false,
                                               defaultValue = "false") boolean includeInactive) {
        return service.list(resource, includeInactive);
    }

    /** Fetch a single item — any authenticated user can read. */
    @GetMapping("/{resource}/{id}")
    @PreAuthorize("isAuthenticated()")
    public MasterItem get(@PathVariable String resource, @PathVariable String id) {
        return service.get(resource, id);
    }

    /** Create a new master item — admin or marketing manager. */
    @PostMapping("/{resource}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<MasterItem> create(@PathVariable String resource,
                                             @Valid @RequestBody MasterItem item) {
        return ResponseEntity.status(201).body(service.create(resource, item));
    }

    /** Update a master item — admin or marketing manager. */
    @PutMapping("/{resource}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public MasterItem update(@PathVariable String resource,
                             @PathVariable String id,
                             @Valid @RequestBody MasterItem item) {
        return service.update(resource, id, item);
    }

    /** Hard-delete a master item — admin or marketing manager. */
    @DeleteMapping("/{resource}/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MARKETING_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable String resource, @PathVariable String id) {
        service.delete(resource, id);
        return ResponseEntity.noContent().build();
    }
}
