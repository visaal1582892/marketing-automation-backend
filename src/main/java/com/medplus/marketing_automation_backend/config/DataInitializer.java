package com.medplus.marketing_automation_backend.config;

import com.medplus.marketing_automation_backend.domain.MasterItem;
import com.medplus.marketing_automation_backend.domain.User;
import com.medplus.marketing_automation_backend.enums.RecordStatus;
import com.medplus.marketing_automation_backend.enums.SkillLevel;
import com.medplus.marketing_automation_backend.enums.MasterTableType;
import com.medplus.marketing_automation_backend.repository.MasterDataRepository;
import com.medplus.marketing_automation_backend.repository.UserRepository;
import com.medplus.marketing_automation_backend.service.UserManagementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * On startup:
 *   1. Ensures the bootstrap admin user exists.
 *   2. Seeds all real marketing team employees (from requirements Section 12)
 *      and two cross-department requestors for end-to-end testing.
 *
 * Password for all seeded test users: Test@1234
 * Idempotent — safe on every boot (skips any user whose email already exists).
 */
@Slf4j
@Component
@Order(2)
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_EMAIL    = "admin@medplus.com";
    private static final String ADMIN_PASSWORD = "medplus@123";
    private static final String ADMIN_ROLE     = "Admin";
    private static final String ADMIN_DEPT     = "Marketing";

    private final NamedParameterJdbcTemplate jdbc;
    private final UserRepository             userRepository;
    private final MasterDataRepository       masterRepository;
    private final PasswordEncoder            passwordEncoder;

    public DataInitializer(NamedParameterJdbcTemplate jdbc,
                           UserRepository userRepository,
                           MasterDataRepository masterRepository,
                           PasswordEncoder passwordEncoder) {
        this.jdbc             = jdbc;
        this.userRepository   = userRepository;
        this.masterRepository = masterRepository;
        this.passwordEncoder  = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        String adminRoleId     = ensureAdminRole();
        String marketingDeptId = lookupDepartmentId(ADMIN_DEPT);
        ensureAdminUser(adminRoleId, marketingDeptId);
        // User seeding is disabled — all users are managed through the admin UI.
    }

    // -------------------------------------------------------------------------
    // Admin bootstrap
    // -------------------------------------------------------------------------

    private String ensureAdminRole() {
        String id = lookupRoleId(ADMIN_ROLE);
        if (id != null) return id;
        return masterRepository.insert(MasterTableType.ROLES, MasterItem.builder()
                .name(ADMIN_ROLE)
                .status(RecordStatus.ACTIVE)
                .build());
    }

    private void ensureAdminUser(String adminRoleId, String marketingDeptId) {
        if (userRepository.findByEmail(ADMIN_EMAIL).isPresent()) {
            log.info("Default admin user already exists ({})", ADMIN_EMAIL);
            return;
        }
        User admin = User.builder()
                .fullName("System Administrator")
                .email(ADMIN_EMAIL)
                .passwordHash(passwordEncoder.encode(ADMIN_PASSWORD))
                .roleIds(adminRoleId != null ? List.of(adminRoleId) : List.of())
                .departmentId(marketingDeptId)
                .skillLevel(SkillLevel.SENIOR)
                .currentActiveTasks(0)
                .status(RecordStatus.ACTIVE)
                .build();
        Long newId = userRepository.create(admin);
        log.info("Seeded default admin user [{}] with id {} (password: {})",
                ADMIN_EMAIL, newId, ADMIN_PASSWORD);
    }

    // -------------------------------------------------------------------------
    // Test-user seeding  (all real employees from Section 12 + 2 requestors)
    // -------------------------------------------------------------------------

    private record TestUser(String fullName, String email,
                             String roleName, String deptName,
                             SkillLevel skill) {}

    private void seedTestUsers() {
        String hash = passwordEncoder.encode(UserManagementService.DEFAULT_PASSWORD);

        List<TestUser> users = List.of(

            // ── Graphic Design & Creative (ROLE-1) ──────────────────────────
            new TestUser("Sampath Kumar",    "sampath@medplus.com",    "Graphic Designer",     "Marketing",        SkillLevel.SENIOR),
            new TestUser("Govardhan P",      "govardhan@medplus.com",  "Graphic Designer",     "Marketing",        SkillLevel.SENIOR),
            new TestUser("Raju G",           "raju@medplus.com",       "Graphic Designer",     "Marketing",        SkillLevel.JUNIOR),

            // ── Photography (ROLE-2) ─────────────────────────────────────────
            new TestUser("Venkatesh R",      "venkatesh@medplus.com",  "Photographer",         "Marketing",        SkillLevel.JUNIOR),

            // ── Content Writer (ROLE-3) ──────────────────────────────────────
            new TestUser("Bodhan K",         "bodhan@medplus.com",     "Content Writer",       "Marketing",        SkillLevel.SENIOR),

            // ── CRM Specialist (ROLE-4) ──────────────────────────────────────
            new TestUser("Aneela T",         "aneela@medplus.com",     "CRM Specialist",       "Marketing",        SkillLevel.SENIOR),

            // ── Paid Ads Manager (ROLE-5) ────────────────────────────────────
            new TestUser("Suresh Yerrabelli","suresh.y@medplus.com",   "Paid Ads Manager",     "Marketing",        SkillLevel.SENIOR),

            // ── SEO Owner (ROLE-6) ───────────────────────────────────────────
            new TestUser("Charan B",         "charan@medplus.com",     "SEO Owner",            "Marketing",        SkillLevel.JUNIOR),

            // ── Procurement Owner (ROLE-7) ───────────────────────────────────
            new TestUser("Ganesh R",         "ganesh.r@medplus.com",   "Procurement Owner",    "Marketing",        SkillLevel.SENIOR),

            // ── Procurement Manager (ROLE-8) ─────────────────────────────────
            new TestUser("Bhadraiah S",      "bhadraiah@medplus.com",  "Procurement Manager",  "Marketing",        SkillLevel.SENIOR),

            // ── Offline Operations (ROLE-9) ──────────────────────────────────
            new TestUser("Suresh D",         "suresh.d@medplus.com",   "Offline Operations",   "Marketing",        SkillLevel.SENIOR),
            new TestUser("Veer Raju",        "veer.raju@medplus.com",  "Offline Operations",   "Marketing",        SkillLevel.JUNIOR),

            // ── ORM Owner (ROLE-10) ──────────────────────────────────────────
            new TestUser("Prabhukumar S",    "prabhukumar@medplus.com","ORM Owner",            "Marketing",        SkillLevel.SENIOR),

            // ── Marketing Manager / Approver ────────────────────────────────
            new TestUser("Purushotham N",    "purushotham@medplus.com","Marketing Manager",    "Marketing",        SkillLevel.SENIOR),
            new TestUser("Mahesh K",         "mahesh@medplus.com",     "Marketing Manager",    "Marketing",        SkillLevel.SENIOR),

            // ── Cross-department Requestors ──────────────────────────────────
            new TestUser("Rajesh Kumar",     "rajesh@medplus.com",     "Requestor",            "Sales Operations", SkillLevel.JUNIOR),
            new TestUser("Priya Sharma",     "priya@medplus.com",      "Requestor",            "Franchise",        SkillLevel.JUNIOR)
        );

        int created = 0;
        for (TestUser u : users) {
            if (userRepository.findByEmail(u.email()).isPresent()) {
                continue;   // already exists — never overwrite live data
            }

            String roleId = lookupRoleId(u.roleName());
            String deptId = lookupDepartmentId(u.deptName());
            if (roleId == null) {
                log.warn("Role '{}' not found in DB — skipping user {}", u.roleName(), u.email());
                continue;
            }

            User user = User.builder()
                    .fullName(u.fullName())
                    .email(u.email())
                    .passwordHash(hash)
                    .roleIds(List.of(roleId))
                    .departmentId(deptId)
                    .skillLevel(u.skill())
                    .currentActiveTasks(0)
                    .status(RecordStatus.ACTIVE)
                    .build();
            userRepository.create(user);
            created++;
        }

        if (created > 0) {
            log.info("Seeded {} new user(s) (default password: {})", created, UserManagementService.DEFAULT_PASSWORD);
        } else {
            log.info("All users already present — no seeding needed.");
        }
    }

    // -------------------------------------------------------------------------
    // Lookup helpers
    // -------------------------------------------------------------------------

    private String lookupRoleId(String roleName) {
        return jdbc.query(
                "SELECT role_id FROM roles WHERE role_name = :name",
                new MapSqlParameterSource("name", roleName),
                rs -> rs.next() ? rs.getString(1) : null);
    }

    private String lookupDepartmentId(String departmentName) {
        return jdbc.query(
                "SELECT department_id FROM departments WHERE department_name = :name",
                new MapSqlParameterSource("name", departmentName),
                rs -> rs.next() ? rs.getString(1) : null);
    }
}
