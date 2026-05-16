package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * V3 – Two-stage QC approval workflow
 *
 * Changes applied (all idempotent – safe to run on a DB that was already
 * partially migrated by FlywaySchemaRepairRunner):
 *
 *  1. Extend campaigns.status ENUM  → add MANAGER_QC_REVIEW, REQUESTOR_QC_REVIEW
 *  2. Migrate campaigns QC_REVIEW   → MANAGER_QC_REVIEW
 *  3. Extend work_tasks.status ENUM → add MANAGER_QC_REVIEW, REQUESTOR_QC_REVIEW
 *  4. Migrate work_tasks QC_REVIEW  → MANAGER_QC_REVIEW
 *  5. Rename work_tasks.completed_at → manager_approved_at  (only if old name exists)
 *  6. Add work_tasks.requestor_approved_at                  (only if missing)
 */
public class V3__QcTwoStageApproval extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V3__QcTwoStageApproval.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // ── 1. Extend campaigns.status ENUM ──────────────────────────────────
        exec(conn,
            "ALTER TABLE campaigns MODIFY COLUMN status "
            + "ENUM('IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW',"
            + "'COMPLETED','REJECTED','CANCELLED') NOT NULL DEFAULT 'IN_PROGRESS'",
            "campaigns.status ENUM extended");

        // ── 2. Migrate legacy campaigns QC_REVIEW → MANAGER_QC_REVIEW ────────
        int migratedCampaigns = update(conn,
            "UPDATE campaigns SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW'");
        if (migratedCampaigns > 0) {
            log.info("V3: migrated {} campaign row(s) from QC_REVIEW → MANAGER_QC_REVIEW", migratedCampaigns);
        }

        // ── 3. Extend work_tasks.status ENUM ─────────────────────────────────
        exec(conn,
            "ALTER TABLE work_tasks MODIFY COLUMN status "
            + "ENUM('ASSIGNED','ACCEPTED','IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REWORK',"
            + "'REQUESTOR_QC_REVIEW','COMPLETED','HELD','CANCELLED','REJECTED') NOT NULL DEFAULT 'ASSIGNED'",
            "work_tasks.status ENUM extended");

        // ── 4. Migrate legacy work_tasks QC_REVIEW → MANAGER_QC_REVIEW ───────
        int migratedTasks = update(conn,
            "UPDATE work_tasks SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW'");
        if (migratedTasks > 0) {
            log.info("V3: migrated {} work_task row(s) from QC_REVIEW → MANAGER_QC_REVIEW", migratedTasks);
        }

        // ── 5. Rename completed_at → manager_approved_at (idempotent) ────────
        if (columnExists(conn, "work_tasks", "completed_at")
                && !columnExists(conn, "work_tasks", "manager_approved_at")) {
            exec(conn,
                "ALTER TABLE work_tasks CHANGE COLUMN completed_at "
                + "manager_approved_at DATETIME NULL DEFAULT NULL",
                "work_tasks.completed_at renamed to manager_approved_at");
        } else if (!columnExists(conn, "work_tasks", "manager_approved_at")) {
            // Neither column exists (e.g. schema was rebuilt from scratch without the column)
            exec(conn,
                "ALTER TABLE work_tasks ADD COLUMN manager_approved_at DATETIME NULL DEFAULT NULL",
                "work_tasks.manager_approved_at added (fallback)");
        } else {
            log.info("V3: work_tasks.manager_approved_at already present – skipping rename");
        }

        // ── 6. Add requestor_approved_at (idempotent) ─────────────────────────
        if (!columnExists(conn, "work_tasks", "requestor_approved_at")) {
            exec(conn,
                "ALTER TABLE work_tasks ADD COLUMN requestor_approved_at DATETIME NULL DEFAULT NULL "
                + "AFTER manager_approved_at",
                "work_tasks.requestor_approved_at added");
        } else {
            log.info("V3: work_tasks.requestor_approved_at already present – skipping");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private void exec(Connection conn, String sql, String desc) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            log.info("V3: {}", desc);
        }
    }

    private int update(Connection conn, String sql) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return ps.executeUpdate();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS "
                   + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }
}
