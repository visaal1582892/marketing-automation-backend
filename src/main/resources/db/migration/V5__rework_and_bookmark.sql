-- =============================================================================
-- V5: Rework status fix, schema cleanup, and bookmarks
--
-- Uses SET + PREPARE for idempotent DDL (safe to re-run on MySQL 5.7 and 8.x).
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- A: Add pre_hold_status to work_tasks
--    Stores the status a task had BEFORE being self-held by the worker so that
--    unhold/resume restores it correctly (REWORK → HELD → REWORK, not → ASSIGNED).
-- ─────────────────────────────────────────────────────────────────────────────
SET @_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'work_tasks'
      AND COLUMN_NAME  = 'pre_hold_status'
);
SET @_sql = IF(@_col = 0,
    'ALTER TABLE work_tasks ADD COLUMN pre_hold_status VARCHAR(20) NULL AFTER status',
    'SELECT 1'
);
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ─────────────────────────────────────────────────────────────────────────────
-- B: Drop the dead campaign_id column from approvals_log
--    This column was always NULL — it was never populated by the application.
--    Campaign context is derived via approvals_log → work_tasks.campaign_id.
-- ─────────────────────────────────────────────────────────────────────────────
SET @_col2 = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'approvals_log'
      AND COLUMN_NAME  = 'campaign_id'
);
SET @_sql2 = IF(@_col2 > 0,
    'ALTER TABLE approvals_log DROP COLUMN campaign_id',
    'SELECT 1'
);
PREPARE _s FROM @_sql2; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ─────────────────────────────────────────────────────────────────────────────
-- C: campaign_bookmarks — requestor bookmarks for quick access
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS campaign_bookmarks (
    user_id     INT      NOT NULL,
    campaign_id INT      NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, campaign_id),
    CONSTRAINT fk_bm_user     FOREIGN KEY (user_id)     REFERENCES users(user_id)     ON DELETE CASCADE,
    CONSTRAINT fk_bm_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(campaign_id) ON DELETE CASCADE
) ENGINE=InnoDB;
