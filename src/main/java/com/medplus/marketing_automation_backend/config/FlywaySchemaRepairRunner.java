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
        ensureTaskTypeIdColumn();
        ensureActionDoneByColumn();
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

    /**
     * Idempotent: ensures task_type_id column exists on the campaigns table
     * and migrates existing requirement_type_id values into it as JSON arrays.
     * Safe to run on every startup — only touches rows that still need migration.
     */
    private void ensureTaskTypeIdColumn() {
        try {
            // 1. Check if the column already exists (compatible with all MySQL versions).
            Integer colCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campaigns' AND COLUMN_NAME = 'task_type_id'",
                Integer.class);
            if (colCount == null || colCount == 0) {
                jdbc.execute(
                    "ALTER TABLE campaigns ADD COLUMN task_type_id VARCHAR(500) NULL AFTER requirement_type_id");
                log.info("FlywaySchemaRepairRunner: task_type_id column added to campaigns table.");
            } else {
                log.info("FlywaySchemaRepairRunner: task_type_id column already exists on campaigns table.");
            }

            // 2. For campaigns that already have a plain (non-JSON) task_type_id, wrap it.
            int wrapped = jdbc.update(
                "UPDATE campaigns SET task_type_id = JSON_ARRAY(task_type_id) "
                + "WHERE task_type_id IS NOT NULL AND task_type_id != '' "
                + "AND LEFT(TRIM(task_type_id), 1) != '['");
            if (wrapped > 0) log.info("FlywaySchemaRepairRunner: wrapped {} plain task_type_id values into JSON arrays.", wrapped);

            // 3. For campaigns with no task_type_id but with a legacy requirement_type_id,
            //    copy and wrap requirement_type_id as a JSON array into task_type_id.
            Integer reqColExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'campaigns' AND COLUMN_NAME = 'requirement_type_id'",
                Integer.class);
            if (reqColExists != null && reqColExists > 0) {
                int migrated = jdbc.update(
                    "UPDATE campaigns SET task_type_id = JSON_ARRAY(requirement_type_id) "
                    + "WHERE (task_type_id IS NULL OR task_type_id = '') "
                    + "AND requirement_type_id IS NOT NULL AND requirement_type_id != ''");
                if (migrated > 0) log.info("FlywaySchemaRepairRunner: migrated {} rows from requirement_type_id to task_type_id.", migrated);

                // 4. Now drop the legacy column.
                jdbc.execute("ALTER TABLE campaigns DROP COLUMN requirement_type_id");
                log.info("FlywaySchemaRepairRunner: requirement_type_id column dropped from campaigns table.");
            }

        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: task_type_id migration failed — {}", e.getMessage());
        }
    }

    /**
     * Idempotent: extends the approvals_log.action_taken ENUM to include HELD and UNHOLD.
     * Safe to run every startup — MySQL silently ignores repeated MODIFY when the definition
     * is already correct.
     */
    private void ensureActionDoneByColumn() {
        try {
            jdbc.execute("ALTER TABLE approvals_log MODIFY COLUMN action_taken "
                + "ENUM('APPROVED','NEEDS_REWORK','REJECTED','REQUESTOR_REWORK','HELD','UNHOLD') NOT NULL");
            log.info("FlywaySchemaRepairRunner: approvals_log action_taken ENUM extended (HELD/UNHOLD).");
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: approvals_log ENUM extension failed — {}", e.getMessage());
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
