package com.medplus.marketing_automation_backend.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * Runs before any other CommandLineRunner (@Order(0)) as a final safety net.
 *
 * <p>The {@link FlywayConfig} strategy already calls repair() + migrate() during
 * Spring context initialization.  This runner guards against the edge case where
 * {@code user_roles} still doesn't exist after that (e.g. a version conflict that
 * Flyway couldn't resolve), by applying the DDL directly via JDBC.
 *
 * <p>It also handles the "full wipe" scenario where core tables were dropped but
 * {@code flyway_schema_history} still records migrations as applied.</p>
 */
@Slf4j
@Component
@Order(0)
public class FlywaySchemaRepairRunner implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    private final DataSource   dataSource;

    public FlywaySchemaRepairRunner(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc       = jdbc;
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        if (!tableExists("departments")) {
            repairFullWipe();
            return;
        }
        if (!tableExists("user_roles")) {
            log.warn("FlywaySchemaRepairRunner: user_roles still missing after context init — applying DDL directly.");
            createUserRolesDirectly();
        }
    }

    private void repairFullWipe() {
        log.warn("FlywaySchemaRepairRunner: `departments` missing — schema wiped. "
                + "Dropping flyway_schema_history and re-running all migrations.");
        try { jdbc.execute("DROP TABLE IF EXISTS `flyway_schema_history`"); }
        catch (Exception e) { log.warn("Could not drop flyway_schema_history: {}", e.getMessage()); }
        flyway().migrate();
        log.info("FlywaySchemaRepairRunner: full re-migrate complete.");
    }

    private void createUserRolesDirectly() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS user_roles ("
                + "  user_id INT NOT NULL,"
                + "  role_id VARCHAR(20) NOT NULL,"
                + "  PRIMARY KEY (user_id, role_id),"
                + "  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,"
                + "  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

            Integer hasCol = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'role_id'",
                Integer.class);
            if (hasCol != null && hasCol > 0) {
                jdbc.execute(
                    "INSERT IGNORE INTO user_roles (user_id, role_id) "
                    + "SELECT user_id, role_id FROM users WHERE role_id IS NOT NULL");
                log.info("FlywaySchemaRepairRunner: user_roles created and seeded from users.role_id.");
            } else {
                log.info("FlywaySchemaRepairRunner: user_roles created (no role_id column to seed from).");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: direct DDL failed — {}", e.getMessage());
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .validateOnMigrate(false)
                .load();
    }

    private boolean tableExists(String tableName) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, tableName);
        return n != null && n > 0;
    }
}
