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
        ensureCampaignColumns();
        ensureWorkTaskColumns();
        ensureTimestampsOnTable("users");
        ensureTimestampsOnTable("granular_tasks");
        ensureTimestampsOnTable("dynamic_questions");
        ensureTimestampsOnTable("role_task_mapping");
        ensureTimestampsOnTable("requirement_role_mapping");
        ensureMasterTableTimestamps();

        // Ensure new tables introduced in V6 and V7 exist on every system.
        // This is a safety net for environments where Flyway skipped those
        // migrations (e.g. orphaned V3/V4 history entries caused ordering issues).
        purgeOrphanedFlywayEntries();
        ensureAutoCreatedTasksTable();
        ensureQcRoutingTable();
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
     * Ensures campaigns table has rejection_reason, created_at, and updated_at columns.
     * These were added to the V1 schema but may be absent in older deployed databases.
     */
    private void ensureCampaignColumns() {
        try {
            if (!columnExists("campaigns", "rejection_reason")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN rejection_reason VARCHAR(1000) NULL");
                log.info("FlywaySchemaRepairRunner: added rejection_reason to campaigns.");
            }
            if (!columnExists("campaigns", "created_at")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added created_at to campaigns.");
            }
            if (!columnExists("campaigns", "updated_at")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added updated_at to campaigns.");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureCampaignColumns failed — {}", e.getMessage());
        }
    }

    /**
     * Ensures work_tasks has created_at/updated_at columns and that its status ENUM
     * includes the REJECTED value added when QC-rejection logic was introduced.
     */
    private void ensureWorkTaskColumns() {
        try {
            if (!columnExists("work_tasks", "created_at")) {
                jdbc.execute("ALTER TABLE work_tasks ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added created_at to work_tasks.");
            }
            if (!columnExists("work_tasks", "updated_at")) {
                jdbc.execute("ALTER TABLE work_tasks ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added updated_at to work_tasks.");
            }
            // Extend the status ENUM to include REJECTED (idempotent — MySQL ignores if already present)
            jdbc.execute("ALTER TABLE work_tasks MODIFY COLUMN status "
                    + "ENUM('ASSIGNED','ACCEPTED','IN_PROGRESS','QC_REVIEW','REWORK',"
                    + "'COMPLETED','HELD','CANCELLED','REJECTED') NOT NULL DEFAULT 'ASSIGNED'");
            log.info("FlywaySchemaRepairRunner: work_tasks status ENUM extended to include REJECTED.");
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureWorkTaskColumns failed — {}", e.getMessage());
        }
    }

    /**
     * Generic helper: adds created_at and updated_at to any named table if absent.
     */
    private void ensureTimestampsOnTable(String table) {
        try {
            if (!columnExists(table, "created_at")) {
                jdbc.execute("ALTER TABLE `" + table + "` ADD COLUMN created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added created_at to {}.", table);
            }
            if (!columnExists(table, "updated_at")) {
                jdbc.execute("ALTER TABLE `" + table + "` ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                log.info("FlywaySchemaRepairRunner: added updated_at to {}.", table);
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureTimestampsOnTable({}) failed — {}", table, e.getMessage());
        }
    }

    /** Adds created_at / updated_at to all master lookup tables. */
    private void ensureMasterTableTimestamps() {
        String[] masterTables = {
            "departments", "roles", "designations", "regions", "task_types",
            "audiences", "languages", "tones", "offer_types", "supporting_proofs",
            "budget_tiers", "kpi_types", "expected_outputs", "business_objectives",
            "vendor_types"
        };
        for (String t : masterTables) {
            if (tableExists(t)) {
                ensureTimestampsOnTable(t);
                backfillNullTimestamps(t);
            }
        }
    }

    /**
     * After adding created_at / updated_at to an existing table the new column
     * defaults to CURRENT_TIMESTAMP for future rows but existing rows that were
     * inserted before the ALTER may still be NULL (depending on MySQL strict mode
     * and the DEFAULT clause applied).  This sets a stable seed value so ORDER BY
     * and NOT NULL constraints work correctly.
     */
    private void backfillNullTimestamps(String table) {
        try {
            int updated = jdbc.update(
                "UPDATE `" + table + "` "
                + "SET created_at = '2024-01-15 10:00:00' WHERE created_at IS NULL");
            if (updated > 0) {
                jdbc.update(
                    "UPDATE `" + table + "` "
                    + "SET updated_at = created_at WHERE updated_at IS NULL");
                log.info("FlywaySchemaRepairRunner: back-filled timestamps for {} row(s) in {}.", updated, table);
            }
        } catch (Exception e) {
            log.warn("FlywaySchemaRepairRunner: backfillNullTimestamps({}) — {}", table, e.getMessage());
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

    /**
     * Removes flyway_schema_history records whose migration FILES no longer exist
     * on disk.  V3 and V4 were merged into V1; V5, V6, and V7 were also merged
     * into V1 so their separate files have been deleted.  Keeping stale entries
     * causes Flyway to flag subsequent migrations as "out of order" and skip them.
     */
    private void purgeOrphanedFlywayEntries() {
        try {
            // V3, V4 – old collaboration migrations folded into V1.
            // V5       – designations/regions timestamps folded into V1.
            // V6       – auto_created_tasks folded into V1.
            // V7       – qc_routing folded into V1.
            String[] orphanedVersions = {"3", "4", "5", "6", "7"};
            for (String v : orphanedVersions) {
                int deleted = jdbc.update(
                    "DELETE FROM flyway_schema_history WHERE version = ?", v);
                if (deleted > 0) {
                    log.info("FlywaySchemaRepairRunner: removed orphaned flyway_schema_history entry for V{}.", v);
                }
            }
        } catch (Exception e) {
            log.warn("FlywaySchemaRepairRunner: could not purge orphaned flyway entries — {}", e.getMessage());
        }
    }

    /**
     * Creates the auto_created_tasks table if it does not yet exist.
     * This is a safety net for existing databases that were deployed before
     * this table was added to V1__complete_schema.sql.
     */
    private void ensureAutoCreatedTasksTable() {
        try {
            if (!tableExists("auto_created_tasks")) {
                jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS auto_created_tasks ("
                    + "  auto_created_task_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  source_task_id           VARCHAR(32)     NOT NULL,"
                    + "  created_task_id          VARCHAR(32)     NOT NULL,"
                    + "  campaign_id              INT             NOT NULL,"
                    + "  source_granular_task_id  VARCHAR(20)     NULL,"
                    + "  content_granular_task_id VARCHAR(20)     NOT NULL,"
                    + "  requested_by_user_id     INT UNSIGNED    NOT NULL,"
                    + "  content_assignee_user_id INT UNSIGNED    NULL,"
                    + "  status ENUM('REQUESTED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'REQUESTED',"
                    + "  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "  UNIQUE KEY uk_auto_created_source (source_task_id),"
                    + "  UNIQUE KEY uk_auto_created_child  (created_task_id),"
                    + "  CONSTRAINT fk_auto_created_source FOREIGN KEY (source_task_id)  REFERENCES work_tasks(task_id) ON DELETE CASCADE,"
                    + "  CONSTRAINT fk_auto_created_child  FOREIGN KEY (created_task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB");
                log.info("FlywaySchemaRepairRunner: auto_created_tasks table created (safety net).");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureAutoCreatedTasksTable failed — {}", e.getMessage());
        }
    }

    /**
     * Creates the qc_routing table if it does not yet exist.
     * Safety net for existing databases deployed before this table was in V1.
     */
    private void ensureQcRoutingTable() {
        try {
            if (!tableExists("qc_routing")) {
                jdbc.execute(
                    "CREATE TABLE IF NOT EXISTS qc_routing ("
                    + "  id              INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + "  worker_role_id  VARCHAR(20) NOT NULL,"
                    + "  manager_role_id VARCHAR(20) NOT NULL,"
                    + "  UNIQUE KEY uq_qc_routing (worker_role_id, manager_role_id),"
                    + "  CONSTRAINT fk_qcr_worker  FOREIGN KEY (worker_role_id)  REFERENCES roles(role_id),"
                    + "  CONSTRAINT fk_qcr_manager FOREIGN KEY (manager_role_id) REFERENCES roles(role_id)"
                    + ") ENGINE=InnoDB");
                log.info("FlywaySchemaRepairRunner: qc_routing table created (safety net).");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureQcRoutingTable failed — {}", e.getMessage());
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

    private boolean columnExists(String tableName, String columnName) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return n != null && n > 0;
    }
}
