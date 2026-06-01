package com.medplus.marketing_automation_backend.config;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

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
        ensureNoTaskTypeIdColumn();
        ensureActionDoneByColumn();
        ensureCampaignColumns();
        ensureWorkTaskColumns();
        ensureTimestampsOnTable("users");
        ensureTimestampsOnTable("granular_tasks");
        ensureTimestampsOnTable("dynamic_questions");
        ensureTimestampsOnTable("role_task_mapping");
        ensureTimestampsOnTable("requirement_role_mapping");
        ensureMasterTableTimestamps();

        purgeOrphanedFlywayEntries();
        ensureAutoCreatedTasksTable();
        ensureQcRoutingTable();
        ensureAutoContentGranularTask();
        ensureCampaignFilesWorkTaskIdColumn();
        ensureNotificationTables();
        ensureCampaignSpecificationTables();
        ensureCampaignSpecificationColumns();
        ensureStoreIdContactNumberColumns();
        // Record V3–V10 in flyway history (idempotent — only inserts missing entries).
        recordMigrationHistory();
        // Final safety-net: run Flyway migrate again so any migration that the Spring
        // autoconfiguration skipped (e.g. due to checksum issues from this runner's
        // earlier history manipulation) gets applied cleanly on this restart.
        try {
            flyway().migrate();
            log.info("FlywaySchemaRepairRunner: safety-net flyway().migrate() complete.");
        } catch (Exception e) {
            log.warn("FlywaySchemaRepairRunner: safety-net migrate failed — {}", e.getMessage());
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

    /**
     * Idempotent: ensures task_type_id is ABSENT from the campaigns table.
     * V8 migration drops it via SQL; this Java safety-net handles the case where
     * Flyway couldn't run V8 (e.g., schema already in repair state).
     * Also cleans up the legacy requirement_type_id column if it still exists.
     */
    private void ensureNoTaskTypeIdColumn() {
        try {
            if (columnExists("campaigns", "task_type_id")) {
                jdbc.execute("ALTER TABLE campaigns DROP COLUMN task_type_id");
                log.info("FlywaySchemaRepairRunner: task_type_id column dropped from campaigns table.");
            }
            if (columnExists("campaigns", "requirement_type_id")) {
                jdbc.execute("ALTER TABLE campaigns DROP COLUMN requirement_type_id");
                log.info("FlywaySchemaRepairRunner: requirement_type_id legacy column dropped from campaigns table.");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureNoTaskTypeIdColumn failed — {}", e.getMessage());
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
            // Extend campaigns status ENUM with new QC stage values
            jdbc.execute("ALTER TABLE campaigns MODIFY COLUMN status "
                    + "ENUM('IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW',"
                    + "'COMPLETED','REJECTED','CANCELLED') NOT NULL DEFAULT 'IN_PROGRESS'");
            log.info("FlywaySchemaRepairRunner: campaigns status ENUM extended to include MANAGER_QC_REVIEW and REQUESTOR_QC_REVIEW.");
            // Migrate existing QC_REVIEW campaigns to MANAGER_QC_REVIEW
            int migratedCamp = jdbc.update(
                    "UPDATE campaigns SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW'");
            if (migratedCamp > 0) {
                log.info("FlywaySchemaRepairRunner: migrated {} campaigns rows from QC_REVIEW to MANAGER_QC_REVIEW.", migratedCamp);
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
            // Extend the status ENUM to include new statuses (idempotent — MySQL ignores if already present)
            jdbc.execute("ALTER TABLE work_tasks MODIFY COLUMN status "
                    + "ENUM('ASSIGNED','ACCEPTED','IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REWORK',"
                    + "'REQUESTOR_QC_REVIEW','COMPLETED','HELD','CANCELLED','REJECTED') NOT NULL DEFAULT 'ASSIGNED'");
            log.info("FlywaySchemaRepairRunner: work_tasks status ENUM extended to include MANAGER_QC_REVIEW and REQUESTOR_QC_REVIEW.");

            // Migrate existing QC_REVIEW rows to MANAGER_QC_REVIEW
            int migratedQc = jdbc.update(
                    "UPDATE work_tasks SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW'");
            if (migratedQc > 0) {
                log.info("FlywaySchemaRepairRunner: migrated {} work_tasks rows from QC_REVIEW to MANAGER_QC_REVIEW.", migratedQc);
            }

            // Rename completed_at → manager_approved_at
            if (columnExists("work_tasks", "completed_at") && !columnExists("work_tasks", "manager_approved_at")) {
                jdbc.execute("ALTER TABLE work_tasks CHANGE COLUMN completed_at manager_approved_at DATETIME NULL DEFAULT NULL");
                log.info("FlywaySchemaRepairRunner: renamed work_tasks.completed_at to manager_approved_at.");
            }

            // Add requestor_approved_at
            if (!columnExists("work_tasks", "requestor_approved_at")) {
                jdbc.execute("ALTER TABLE work_tasks ADD COLUMN requestor_approved_at DATETIME NULL DEFAULT NULL AFTER manager_approved_at");
                log.info("FlywaySchemaRepairRunner: added requestor_approved_at to work_tasks.");
            }
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
     * Idempotent: extends the approvals_log.action_taken ENUM to include HELD, UNHOLD, and CANCELLED.
     * Safe to run every startup — MySQL silently ignores repeated MODIFY when the definition
     * is already correct.
     */
    private void ensureActionDoneByColumn() {
        try {
            jdbc.execute("ALTER TABLE approvals_log MODIFY COLUMN action_taken "
                + "ENUM('APPROVED','NEEDS_REWORK','REJECTED','REQUESTOR_REWORK','HELD','UNHOLD','CANCELLED') NOT NULL");
            log.info("FlywaySchemaRepairRunner: approvals_log action_taken ENUM extended (HELD/UNHOLD/CANCELLED).");
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: approvals_log ENUM extension failed — {}", e.getMessage());
        }
    }

    /**
     * Removes flyway_schema_history records whose migration FILES no longer exist on disk.
     * The old V6 (auto_created_tasks) and old V7 (qc_routing) were both folded into V1.
     * Those file-less entries were previously purged. Now both V6 and V7 are live SQL
     * files (CampaignSpecifications and CampaignTaskConfig) — do NOT purge them.
     * V3, V4, V5 are also live SQL files and must NOT be purged.
     */
    private void purgeOrphanedFlywayEntries() {
        try {
            // All previously-orphaned versions (old V6, old V7) have been superseded by
            // real migration files. Nothing to purge any more.
            String[] orphanedVersions = {};
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
     * Ensures V3-V10 have a success=1 row in flyway_schema_history so Flyway
     * never re-runs them.  Only removes FAILED (success=0) entries; existing
     * successful rows are left untouched so their checksums stay correct and
     * Flyway's repair step does not strip them out.
     */
    private void recordMigrationHistory() {
        try {
            Object[][] migrations = {
                {"3",  "QcTwoStageApproval",           "V3__QcTwoStageApproval.sql"},
                {"4",  "NotificationSystem",            "V4__NotificationSystem.sql"},
                {"5",  "NotificationStatus",            "V5__NotificationStatus.sql"},
                {"6",  "CampaignSpecifications",        "V6__CampaignSpecifications.sql"},
                {"7",  "CampaignTaskConfig",            "V7__CampaignTaskConfig.sql"},
                {"8",  "RemoveTaskTypeIdFromCampaigns", "V8__RemoveTaskTypeIdFromCampaigns.sql"},
                {"9",  "RemoveCampaignDeliverables",    "V9__RemoveCampaignDeliverables.sql"},
                {"10", "AddStoreIdContactNumber",       "V10__AddStoreIdContactNumber.sql"},
            };
            for (Object[] m : migrations) {
                String version = (String) m[0];
                String desc    = (String) m[1];
                String script  = (String) m[2];
                // Remove only FAILED entries — never touch a successful row so checksums stay intact
                jdbc.update("DELETE FROM flyway_schema_history WHERE version = ? AND success = 0", version);
                Integer alreadyOk = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1",
                    Integer.class, version);
                if (alreadyOk != null && alreadyOk > 0) {
                    continue; // already recorded correctly
                }
                Integer maxRank = jdbc.queryForObject(
                    "SELECT COALESCE(MAX(installed_rank), 0) FROM flyway_schema_history", Integer.class);
                int rank = (maxRank == null ? 0 : maxRank) + 1;
                jdbc.update(
                    "INSERT INTO flyway_schema_history "
                    + "(installed_rank, version, description, type, script, checksum, installed_by, execution_time, success) "
                    + "VALUES (?, ?, ?, 'SQL', ?, 0, 'java-runner', 1, 1)",
                    rank, version, desc, script);
                log.info("FlywaySchemaRepairRunner: recorded V{} ({}) in flyway_schema_history.", version, desc);
            }
        } catch (Exception e) {
            log.warn("FlywaySchemaRepairRunner: recordMigrationHistory failed — {}", e.getMessage());
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

    /**
     * Ensures the granular_tasks row and role mappings for the "Need Content?"
     * feature exist on every startup, for both fresh and existing databases.
     *
     * <ul>
     *   <li>Removes any stale row keyed with underscores ("TASK_AUTO_CONTENT").</li>
     *   <li>Inserts the correct "TASK-AUTO-CONTENT" row (task_type_id '4' = Content Writing).</li>
     *   <li>Maps the task to Content Writer (role '4') and CRM Specialist (role '5')
     *       so the routing engine can assign it automatically.</li>
     * </ul>
     *
     * AutoCreatedTaskService uses the hardcoded constant "TASK-AUTO-CONTENT" (hyphens);
     * the granular_tasks row must carry the exact same key to satisfy fk_wt_gran_task.
     */
    private void ensureAutoContentGranularTask() {
        try {
            // Remove any incorrectly keyed row inserted manually with underscores
            Integer wrongRow = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM granular_tasks WHERE task_id = 'TASK_AUTO_CONTENT'",
                    Integer.class);
            if (wrongRow != null && wrongRow > 0) {
                jdbc.update("DELETE FROM granular_tasks WHERE task_id = 'TASK_AUTO_CONTENT'");
                log.info("FlywaySchemaRepairRunner: removed incorrectly keyed TASK_AUTO_CONTENT row.");
            }

            // Ensure the correctly keyed granular task row exists
            Integer correct = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM granular_tasks WHERE task_id = 'TASK-AUTO-CONTENT'",
                    Integer.class);
            if (correct == null || correct == 0) {
                jdbc.update(
                        "INSERT IGNORE INTO granular_tasks "
                        + "(task_id, task_name, task_type_id, task_category, status) "
                        + "VALUES ('TASK-AUTO-CONTENT', 'Auto Generated Content Writing', '4', 'OFFLINE', 'ACTIVE')");
                log.info("FlywaySchemaRepairRunner: inserted TASK-AUTO-CONTENT into granular_tasks.");
            }

            // Map to Content Writer (role '4') and CRM Specialist (role '5')
            // INSERT IGNORE is safe — unique key uq_role_task prevents duplicates
            jdbc.update("INSERT IGNORE INTO role_task_mapping (role_id, task_id) VALUES ('4', 'TASK-AUTO-CONTENT')");
            jdbc.update("INSERT IGNORE INTO role_task_mapping (role_id, task_id) VALUES ('5', 'TASK-AUTO-CONTENT')");
            log.info("FlywaySchemaRepairRunner: ensured role_task_mapping for TASK-AUTO-CONTENT (roles 4, 5).");

        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureAutoContentGranularTask failed — {}", e.getMessage());
        }
    }

    /**
     * Adds the nullable work_task_id column to campaign_files so files can be
     * either campaign-level (NULL) or tied to a specific work task (VARCHAR(20) FK).
     * Existing rows keep NULL automatically — no data migration needed.
     */
    private void ensureCampaignFilesWorkTaskIdColumn() {
        try {
            if (!columnExists("campaign_files", "work_task_id")) {
                jdbc.execute(
                    "ALTER TABLE campaign_files " +
                    "ADD COLUMN work_task_id VARCHAR(20) NULL DEFAULT NULL AFTER campaign_id, " +
                    "ADD CONSTRAINT fk_cf_work_task FOREIGN KEY (work_task_id) " +
                    "  REFERENCES work_tasks(task_id) ON DELETE SET NULL");
                log.info("FlywaySchemaRepairRunner: added work_task_id column to campaign_files.");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureCampaignFilesWorkTaskIdColumn failed — {}", e.getMessage());
        }
    }

    /**
     * Safety net for the V4 notification system tables.
     * Creates notification_event_types, notification_templates, and notifications
     * if they do not exist, then seeds the default event types and templates.
     * Idempotent — INSERT IGNORE and CREATE TABLE IF NOT EXISTS guard against duplicates.
     */
    private void ensureNotificationTables() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS notification_event_types ("
                + "  id          BIGINT       AUTO_INCREMENT PRIMARY KEY,"
                + "  event_type  VARCHAR(100) NOT NULL UNIQUE,"
                + "  description VARCHAR(500),"
                + "  created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS notification_templates ("
                + "  id               BIGINT        AUTO_INCREMENT PRIMARY KEY,"
                + "  event_type       VARCHAR(100)  NOT NULL,"
                + "  role_id          VARCHAR(20)   NULL,"
                + "  message_template VARCHAR(1000) NOT NULL,"
                + "  url_template     VARCHAR(500)  NOT NULL,"
                + "  created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "  UNIQUE KEY uq_template_event_role (event_type, role_id)"
                + ") ENGINE=InnoDB");

            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS notifications ("
                + "  id           BIGINT        AUTO_INCREMENT PRIMARY KEY,"
                + "  user_id      BIGINT        NOT NULL,"
                + "  event_type   VARCHAR(100)  NOT NULL,"
                + "  message      VARCHAR(1000) NOT NULL,"
                + "  url          VARCHAR(500)  NOT NULL,"
                + "  is_read      TINYINT(1)    NOT NULL DEFAULT 0,"
                + "  reference_id VARCHAR(50)   NULL,"
                + "  status       TINYINT(1)    NOT NULL DEFAULT 1,"
                + "  created_at   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  INDEX idx_notifications_user        (user_id),"
                + "  INDEX idx_notifications_user_unread (user_id, is_read),"
                + "  INDEX idx_notif_reference           (reference_id, event_type, status)"
                + ") ENGINE=InnoDB");
            // Idempotently add new columns — use columnExists() to avoid IF NOT EXISTS
            // syntax issues on older MySQL 8.0 patch versions.
            if (!columnExists("notifications", "reference_id")) {
                try {
                    jdbc.execute("ALTER TABLE notifications ADD COLUMN reference_id VARCHAR(50) NULL");
                    log.info("FlywaySchemaRepairRunner: added reference_id column to notifications.");
                } catch (Exception e) {
                    log.error("FlywaySchemaRepairRunner: failed to add reference_id — {}", e.getMessage());
                }
            }
            if (!columnExists("notifications", "status")) {
                try {
                    jdbc.execute("ALTER TABLE notifications ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1");
                    log.info("FlywaySchemaRepairRunner: added status column to notifications.");
                } catch (Exception e) {
                    log.error("FlywaySchemaRepairRunner: failed to add status — {}", e.getMessage());
                }
            }
            log.info("FlywaySchemaRepairRunner: notifications columns → reference_id={}, status={}",
                columnExists("notifications", "reference_id"),
                columnExists("notifications", "status"));

            // Seed event types
            String[] eventTypes = {
                "TASK_ASSIGNED",          "Fired when a task is assigned to a user",
                "ADDED_TO_COLLABORATION", "Fired when a user is added to a task collaboration",
                "NEW_TASK_MESSAGE",       "Fired when a new chat message is posted in a collaboration",
                "SUBMITTED_FOR_QC",       "Fired when a worker submits their task for QC review",
                "MANAGER_QC_APPROVAL",    "Fired when a manager approves a task during QC",
                "REQUESTOR_QC_APPROVAL",  "Fired when a requestor approves a completed task",
                "TASK_HELD_BY_MANAGER",   "Fired when a manager places a task on hold",
                "TASK_CANCELLED",         "Fired when a manager cancels an assigned task",
                "CAMPAIGN_DELETED",       "Fired when a requestor deletes an entire campaign",
                "COMMENT_ADDED",          "Fired when a worker adds a comment and self-holds their task",
                "COMMENT_RESPONDED",      "Fired when a requestor/manager marks a worker comment as answered",
                "MANAGER_REWORK",         "Fired when a manager sends a task back for rework",
                "REQUESTOR_REWORK",       "Fired when a requestor sends a task back for rework",
                "MANAGER_REJECT",            "Fired when a manager rejects a task during QC",
                "CONTENT_TASK_SUBMITTED",    "Fired when a content writer submits their auto-generated content task",
                "CONTENT_TASK_AUTO_CLOSED",  "Fired when a content task is auto-completed because the designer submitted for QC"
            };
            for (int i = 0; i < eventTypes.length; i += 2) {
                jdbc.update(
                    "INSERT IGNORE INTO notification_event_types (event_type, description) VALUES (?, ?)",
                    eventTypes[i], eventTypes[i + 1]);
            }

            // Remove duplicate templates (MySQL UNIQUE allows multiple NULL role_id)
            jdbc.execute("""
                    DELETE t1 FROM notification_templates t1
                    INNER JOIN notification_templates t2
                      ON t1.event_type = t2.event_type
                     AND t1.role_id IS NULL
                     AND t2.role_id IS NULL
                     AND t1.id > t2.id
                    """);
            jdbc.execute("""
                    DELETE t1 FROM notification_templates t1
                    INNER JOIN notification_templates t2
                      ON t1.event_type = t2.event_type
                     AND t1.role_id = t2.role_id
                     AND t1.role_id IS NOT NULL
                     AND t1.id > t2.id
                    """);

            // Seed default templates only when (event_type, role_id) pair missing
            Object[][] templates = {
                {"TASK_ASSIGNED",          null, "A new task {taskId} has been assigned to you",                                    "/my-tasks"},
                {"ADDED_TO_COLLABORATION", null, "{inviterName} added you to collaboration on task {taskId}",                        "/collaborations?taskId={taskId}"},
                {"NEW_TASK_MESSAGE",       null, "{senderName} sent a message in task {taskId}",                                     "/collaborations?taskId={taskId}"},
                {"SUBMITTED_FOR_QC",       null, "{workerName} submitted task {taskId} for QC review",                              "/manager/qc-review"},
                {"MANAGER_QC_APPROVAL",    null, "Manager approved your task {taskId}. Awaiting requestor sign-off.",               "/my-tasks"},
                {"MANAGER_QC_APPROVAL",    "12", "Task {taskId} has passed manager QC and is ready for your review",                "/requestor-qc-review?taskId={taskId}"},
                {"REQUESTOR_QC_APPROVAL",  null, "{requestorName} approved your task {taskId}",                                     "/my-tasks"},
                {"TASK_HELD_BY_MANAGER",   null, "Manager {managerName} has held your task {taskId}",                              "/my-tasks"},
                {"TASK_CANCELLED",         null, "Your task {taskId} has been cancelled by {managerName}",                         "/my-tasks"},
                {"CAMPAIGN_DELETED",       null, "Campaign {campaignName} has been deleted. Your assigned task is no longer active.", "/my-tasks"},
                {"COMMENT_ADDED",          null, "{workerName} added a comment on task {taskId} — please check it out",            "/campaigns"},
                {"COMMENT_RESPONDED",      null, "{responderName} responded to your comment on task {taskId}",                     "/my-tasks"},
                {"MANAGER_REWORK",         null, "Manager {managerName} has requested rework on your task {taskId}",               "/my-tasks"},
                {"REQUESTOR_REWORK",       null, "{requestorName} has requested rework on your task {taskId}",                     "/my-tasks"},
                {"MANAGER_REJECT",           null, "Manager {managerName} has rejected your task {taskId}",                                        "/my-tasks"},
                {"CONTENT_TASK_SUBMITTED",   null, "{writerName} has completed the content writing task {taskId} — please review it",             "/my-tasks"},
                {"CONTENT_TASK_AUTO_CLOSED", null, "Your content writing task {taskId} has been marked as complete (designer submitted for QC)",   "/my-tasks"}
            };
            for (Object[] t : templates) {
                insertNotificationTemplateIfAbsent((String) t[0], (String) t[1], (String) t[2], (String) t[3]);
            }

            log.info("FlywaySchemaRepairRunner: notification tables and seed data ensured.");
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureNotificationTables failed — {}", e.getMessage());
        }
    }

    /**
     * V6 — Campaign Specifications lookup tables and hierarchical mapping tables.
     * Creates all six tables if they do not yet exist and seeds initial data.
     * Idempotent — CREATE TABLE IF NOT EXISTS + INSERT IGNORE prevent duplicates.
     */
    private void ensureCampaignSpecificationTables() {
        try {
            // campaign_types
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS campaign_types ("
                + "  campaign_type_id   VARCHAR(20)               NOT NULL PRIMARY KEY,"
                + "  campaign_type_name VARCHAR(200)              NOT NULL UNIQUE,"
                + "  status             ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',"
                + "  created_at         TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at         TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

            // business_verticals
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS business_verticals ("
                + "  business_vertical_id   VARCHAR(20)               NOT NULL PRIMARY KEY,"
                + "  business_vertical_name VARCHAR(200)              NOT NULL UNIQUE,"
                + "  status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',"
                + "  created_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

            // business_types
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS business_types ("
                + "  business_type_id   VARCHAR(20)               NOT NULL PRIMARY KEY,"
                + "  business_type_name VARCHAR(200)              NOT NULL UNIQUE,"
                + "  status             ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',"
                + "  created_at         TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at         TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

            // store_format_types
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS store_format_types ("
                + "  store_format_type_id   VARCHAR(20)               NOT NULL PRIMARY KEY,"
                + "  store_format_type_name VARCHAR(200)              NOT NULL UNIQUE,"
                + "  status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',"
                + "  created_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB");

            // business_vertical_business_type_mapping
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS business_vertical_business_type_mapping ("
                + "  mapping_id           INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "  business_vertical_id VARCHAR(20) NOT NULL,"
                + "  business_type_id     VARCHAR(20) NOT NULL,"
                + "  UNIQUE KEY uq_bv_bt (business_vertical_id, business_type_id),"
                + "  CONSTRAINT fk_bvbt_vertical FOREIGN KEY (business_vertical_id)"
                + "    REFERENCES business_verticals(business_vertical_id) ON DELETE CASCADE,"
                + "  CONSTRAINT fk_bvbt_type     FOREIGN KEY (business_type_id)"
                + "    REFERENCES business_types(business_type_id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

            // business_type_store_format_mapping
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS business_type_store_format_mapping ("
                + "  mapping_id            INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "  business_type_id      VARCHAR(20) NOT NULL,"
                + "  store_format_type_id  VARCHAR(20) NOT NULL,"
                + "  UNIQUE KEY uq_bt_sft (business_type_id, store_format_type_id),"
                + "  CONSTRAINT fk_btsf_type   FOREIGN KEY (business_type_id)"
                + "    REFERENCES business_types(business_type_id) ON DELETE CASCADE,"
                + "  CONSTRAINT fk_btsf_format FOREIGN KEY (store_format_type_id)"
                + "    REFERENCES store_format_types(store_format_type_id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB");

            log.info("FlywaySchemaRepairRunner: Campaign Specification tables ensured.");

            // Seed campaign_types
            jdbc.update("INSERT IGNORE INTO campaign_types (campaign_type_id, campaign_type_name) VALUES ('1','Branding')");
            jdbc.update("INSERT IGNORE INTO campaign_types (campaign_type_id, campaign_type_name) VALUES ('2','Marketing')");

            // Seed business_verticals
            jdbc.update("INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES ('1','Pharma Retail')");
            jdbc.update("INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES ('2','Diagnostics')");
            jdbc.update("INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES ('3','Opticals')");
            jdbc.update("INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES ('4','Insurance')");
            jdbc.update("INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES ('5','Non-Pharma Retail')");

            // Seed business_types
            jdbc.update("INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES ('1','COCO')");
            jdbc.update("INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES ('2','COFO')");
            jdbc.update("INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES ('3','FOFO')");
            jdbc.update("INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES ('4','Collection Center')");
            jdbc.update("INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES ('5','Diagnostic Center')");

            // Seed store_format_types
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('1','Rural')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('2','Urban Regular')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('3','Large Format')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('4','Home Collection')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('5','Walkin')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('6','L1')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('7','L2')");
            jdbc.update("INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES ('8','L3')");

            // Seed business_vertical → business_type mappings
            // Pharma Retail: COCO, COFO, FOFO
            jdbc.update("INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES ('1','1')");
            jdbc.update("INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES ('1','2')");
            jdbc.update("INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES ('1','3')");
            // Diagnostics: Collection Center, Diagnostic Center
            jdbc.update("INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES ('2','4')");
            jdbc.update("INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES ('2','5')");

            // Seed business_type → store_format_type mappings
            // COCO: Rural, Urban Regular, Large Format
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('1','1')");
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('1','2')");
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('1','3')");
            // Collection Center: Home Collection, Walkin
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('4','4')");
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('4','5')");
            // Diagnostic Center: L1, L2, L3
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('5','6')");
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('5','7')");
            jdbc.update("INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES ('5','8')");

            log.info("FlywaySchemaRepairRunner: Campaign Specification seed data ensured.");

            // campaign_task_config — one row per task in a combination
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS campaign_task_config ("
                + "  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                + "  campaign_type_id     VARCHAR(20)               NOT NULL DEFAULT '',"
                + "  business_vertical_id VARCHAR(20)               NOT NULL DEFAULT '',"
                + "  business_type_id     VARCHAR(20)               NOT NULL DEFAULT '',"
                + "  store_format_type_id VARCHAR(20)               NOT NULL DEFAULT '',"
                + "  granular_task_id     VARCHAR(20)               NOT NULL,"
                + "  status               ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',"
                + "  created_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "  updated_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                + "  UNIQUE KEY uq_ctc (campaign_type_id, business_vertical_id, business_type_id, store_format_type_id, granular_task_id),"
                + "  CONSTRAINT fk_ctc_task FOREIGN KEY (granular_task_id) REFERENCES granular_tasks(task_id) ON DELETE CASCADE"
                + ") ENGINE=InnoDB");
            log.info("FlywaySchemaRepairRunner: campaign_task_config table ensured.");

        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureCampaignSpecificationTables failed — {}", e.getMessage());
        }
    }

    /**
     * V6 — Adds the four Campaign Specification FK columns to the campaigns table.
     * Uses columnExists() check (compatible with all MySQL 8.0 versions) rather
     * than ALTER TABLE … ADD COLUMN IF NOT EXISTS which requires MySQL 8.0.3+.
     */
    private void ensureCampaignSpecificationColumns() {
        try {
            if (!columnExists("campaigns", "campaign_type_id")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN campaign_type_id VARCHAR(20) NULL");
                log.info("FlywaySchemaRepairRunner: added campaign_type_id to campaigns.");
            }
            if (!columnExists("campaigns", "business_vertical_id")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN business_vertical_id VARCHAR(20) NULL");
                log.info("FlywaySchemaRepairRunner: added business_vertical_id to campaigns.");
            }
            if (!columnExists("campaigns", "business_type_id")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN business_type_id VARCHAR(20) NULL");
                log.info("FlywaySchemaRepairRunner: added business_type_id to campaigns.");
            }
            if (!columnExists("campaigns", "store_format_type_id")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN store_format_type_id VARCHAR(20) NULL");
                log.info("FlywaySchemaRepairRunner: added store_format_type_id to campaigns.");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureCampaignSpecificationColumns failed — {}", e.getMessage());
        }
    }

    /**
     * V10 — Adds store_id and contact_number to the campaigns table (idempotent).
     */
    private void ensureStoreIdContactNumberColumns() {
        try {
            if (!columnExists("campaigns", "store_id")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN store_id VARCHAR(100) NULL");
                log.info("FlywaySchemaRepairRunner: added store_id to campaigns.");
            }
            if (!columnExists("campaigns", "contact_number")) {
                jdbc.execute("ALTER TABLE campaigns ADD COLUMN contact_number VARCHAR(20) NULL");
                log.info("FlywaySchemaRepairRunner: added contact_number to campaigns.");
            }
        } catch (Exception e) {
            log.error("FlywaySchemaRepairRunner: ensureStoreIdContactNumberColumns failed — {}", e.getMessage());
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

    /**
     * Inserts a notification template only if no row exists for the same event_type + role_id.
     * Uses null-safe match because MySQL UNIQUE allows multiple NULL role_id values.
     */
    private void insertNotificationTemplateIfAbsent(String eventType, String roleId,
                                                    String messageTemplate, String urlTemplate) {
        Integer exists = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notification_templates
                 WHERE event_type = ?
                   AND ((? IS NULL AND role_id IS NULL) OR role_id = ?)
                """, Integer.class, eventType, roleId, roleId);
        if (exists != null && exists > 0) {
            return;
        }
        jdbc.update(
                "INSERT INTO notification_templates (event_type, role_id, message_template, url_template) VALUES (?, ?, ?, ?)",
                eventType, roleId, messageTemplate, urlTemplate);
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, tableName, columnName);
        return n != null && n > 0;
    }
}
