package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.dto.UserRequest;
import com.medplus.marketing_automation_backend.dto.UserResponse;
import com.medplus.marketing_automation_backend.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasAnyRole('ADMIN', 'MARKETING_MANAGER')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserResponse> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return userManagementService.list(includeInactive);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable long id) {
        return userManagementService.get(id);
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
        return ResponseEntity.status(201).body(userManagementService.create(req));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable long id, @Valid @RequestBody UserRequest req) {
        return userManagementService.update(id, req);
    }

    /** Toggle active/inactive status for the user. */
    @PatchMapping("/{id}/toggle-active")
    public UserResponse toggleActive(@PathVariable long id) {
        return userManagementService.toggleActive(id);
    }

    /** Reset a user's password back to the default (medplus@123). */
    @PatchMapping("/{id}/reset-password")
    public ResponseEntity<Void> resetPassword(@PathVariable long id) {
        userManagementService.resetPassword(id);
        return ResponseEntity.noContent().build();
    }

    /** Hard-delete a user. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        userManagementService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
