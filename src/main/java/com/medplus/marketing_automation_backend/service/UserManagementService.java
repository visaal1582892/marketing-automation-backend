package com.medplus.marketing_automation_backend.service;

import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.dto.PagedResponse;
import com.medplus.marketing_automation_backend.dto.UserRequest;
import com.medplus.marketing_automation_backend.dto.UserResponse;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.exception.BadRequestException;
import com.medplus.marketing_automation_backend.exception.ResourceNotFoundException;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserManagementService {

    public static final String DEFAULT_PASSWORD = "medplus@123";

    private final UserRepository  userRepo;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(UserRepository userRepo, PasswordEncoder passwordEncoder) {
        this.userRepo        = userRepo;
        this.passwordEncoder = passwordEncoder;
    }

    // ── Read ──────────────────────────────────────────────────────────────────────

    public List<UserResponse> list(boolean includeInactive) {
        return userRepo.findAll(includeInactive).stream()
                .map(UserManagementService::toResponse)
                .collect(Collectors.toList());
    }

    /** Paginated user list with optional column-level filters. */
    public PagedResponse<UserResponse> listPaged(
            boolean includeInactive,
            String name, String email, String roleName,
            String departmentId, String designationId,
            String skillLevel, String status,
            int page, int size) {

        PagedResponse<User> raw = userRepo.findAllPaged(
                includeInactive, name, email, roleName,
                departmentId, designationId, skillLevel, status, page, size);

        List<UserResponse> mapped = raw.content().stream()
                .map(UserManagementService::toResponse)
                .collect(Collectors.toList());
        return PagedResponse.of(mapped, raw.totalElements(), raw.page(), raw.size());
    }

    public UserResponse get(long userId) {
        return userRepo.findById(userId)
                .map(UserManagementService::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    // ── Create  (password always defaults to medplus@123) ────────────────────────

    @Transactional
    public UserResponse create(UserRequest req) {
        log.info("USER create | email={} roles={} department={} designation={}",
                req.getEmail(), req.getRoleIds(), req.getDepartmentId(), req.getDesignationId());
        if (userRepo.findByEmail(req.getEmail()).isPresent()) {
            log.warn("USER create failed — email already in use | email={}", req.getEmail());
            throw new BadRequestException("Email already in use: " + req.getEmail());
        }

        List<String> roleIds = safeRoleIds(req.getRoleIds());

        User user = User.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(DEFAULT_PASSWORD))
                .departmentId(req.getDepartmentId())
                .roleIds(roleIds)       // used by userRepo.create to insert user_roles
                .designationId(req.getDesignationId())
                .skillLevel(req.getSkillLevel())
                .currentActiveTasks(0)
                .status(req.getStatus() != null ? req.getStatus() : RecordStatus.ACTIVE)
                .build();

        Long id = userRepo.create(user);
        log.info("USER created | userId={} email={} roles={}", id, req.getEmail(), roleIds);
        return get(id);
    }

    // ── Update  (profile fields only — password handled separately) ──────────────

    @Transactional
    public UserResponse update(long userId, UserRequest req) {
        log.info("USER update | userId={} email={} roles={} designation={}",
                userId, req.getEmail(), req.getRoleIds(), req.getDesignationId());
        User existing = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!existing.getEmail().equalsIgnoreCase(req.getEmail())) {
            if (userRepo.findByEmail(req.getEmail()).isPresent()) {
                throw new BadRequestException("Email already in use: " + req.getEmail());
            }
        }

        List<String> roleIds = safeRoleIds(req.getRoleIds());

        User updated = User.builder()
                .userId(userId)
                .fullName(req.getFullName())
                .email(req.getEmail())
                .departmentId(req.getDepartmentId())
                .roleIds(roleIds)
                .designationId(req.getDesignationId())
                .skillLevel(req.getSkillLevel())
                .build();

        userRepo.update(updated);
        userRepo.replaceUserRoles(userId, roleIds);
        log.info("USER updated | userId={} roles={}", userId, roleIds);

        return get(userId);
    }

    // ── Change password  (user supplies current + new password) ──────────────────

    @Transactional
    public void changePassword(long userId, String currentPassword, String newPassword) {
        log.info("USER changePassword | userId={}", userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            log.warn("USER changePassword failed — wrong current password | userId={}", userId);
            throw new BadRequestException("Current password is incorrect.");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new BadRequestException("New password must not be blank.");
        }

        userRepo.updatePassword(userId, passwordEncoder.encode(newPassword));
        log.info("USER password changed | userId={}", userId);
    }

    // ── Reset password  (admin resets a user back to the default password) ────────

    @Transactional
    public void resetPassword(long userId) {
        log.info("USER resetPassword (admin) | userId={}", userId);
        userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userRepo.updatePassword(userId, passwordEncoder.encode(DEFAULT_PASSWORD));
        log.info("USER password reset to default | userId={}", userId);
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(long userId) {
        log.info("USER delete | userId={}", userId);
        userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        userRepo.delete(userId);
        log.info("USER deleted | userId={}", userId);
    }

    // ── Toggle active status ─────────────────────────────────────────────────────

    @Transactional
    public UserResponse toggleActive(long userId) {
        log.info("USER toggleActive | userId={}", userId);
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        RecordStatus newStatus = user.getStatus() == RecordStatus.ACTIVE
                ? RecordStatus.INACTIVE
                : RecordStatus.ACTIVE;
        userRepo.updateStatus(userId, newStatus);
        log.info("USER status toggled | userId={} previousStatus={} newStatus={}",
                userId, user.getStatus(), newStatus);
        return get(userId);
    }

    // ── Capacity summary (for manager dashboard) ─────────────────────────────────

    public List<UserResponse> getCapacityByRole(String roleId) {
        return userRepo.findByRole(roleId).stream()
                .map(UserManagementService::toResponse)
                .collect(Collectors.toList());
    }

    // ── Mapping ───────────────────────────────────────────────────────────────────

    public static UserResponse toResponse(User u) {
        return UserResponse.builder()
                .userId(u.getUserId())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .departmentId(u.getDepartmentId())
                .departmentName(u.getDepartmentName())
                .role(u.getPrimaryRoleName())
                .roleIds(u.getRoleIds())
                .roleNames(u.getRoleNames())
                .designationId(u.getDesignationId())
                .designationName(u.getDesignationName())
                .skillLevel(u.getSkillLevel() == null ? null : u.getSkillLevel().name())
                .currentActiveTasks(u.getCurrentActiveTasks())
                .status(u.getStatus() == null ? null : u.getStatus().name())
                .createdAt(u.getCreatedAt())
                .build();
    }

    private static List<String> safeRoleIds(List<String> roleIds) {
        if (roleIds == null) return Collections.emptyList();
        return roleIds.stream().filter(r -> r != null && !r.isBlank()).collect(Collectors.toList());
    }
}
