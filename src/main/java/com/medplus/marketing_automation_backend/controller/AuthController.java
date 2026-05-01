package com.medplus.marketing_automation_backend.controller;

import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.dto.ChangePasswordRequest;
import com.medplus.marketing_automation_backend.dto.LoginRequest;
import com.medplus.marketing_automation_backend.dto.LoginResponse;
import com.medplus.marketing_automation_backend.security.CustomUserDetails;
import com.medplus.marketing_automation_backend.service.AuthService;
import com.medplus.marketing_automation_backend.service.UserManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService            authService;
    private final UserManagementService  userManagementService;

    public AuthController(AuthService authService, UserManagementService userManagementService) {
        this.authService           = authService;
        this.userManagementService = userManagementService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }
        User u = principal.getUser();
        return ResponseEntity.ok(Map.of(
                "id",            u.getUserId(),
                "fullName",      u.getFullName(),
                "email",         u.getEmail(),
                "role",          u.getPrimaryRoleName()   == null ? "" : u.getPrimaryRoleName(),
                "roles",         u.getRoleNames()         == null ? Collections.emptyList() : u.getRoleNames(),
                "roleIds",       u.getRoleIds()           == null ? Collections.emptyList() : u.getRoleIds(),
                "department",    u.getDepartmentName()    == null ? "" : u.getDepartmentName(),
                "departmentId",  u.getDepartmentId()      == null ? "" : u.getDepartmentId(),
                "designation",   u.getDesignationName()   == null ? "" : u.getDesignationName(),
                "designationId", u.getDesignationId()     == null ? "" : u.getDesignationId()
        ));
    }

    /**
     * Allows the currently logged-in user to change their own password.
     */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody ChangePasswordRequest req) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        userManagementService.changePassword(
                principal.getUser().getUserId(),
                req.getCurrentPassword(),
                req.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
