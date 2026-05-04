-- =============================================================================
-- V6: Fix approvals_log cleanup and create campaign_bookmarks
--
-- V5 section B failed because MySQL requires dropping the FK constraint before
-- dropping the column.  V6 re-does both steps idempotently:
--   B1 – drop FK  fk_al_campaign  (if it still exists)
--   B2 – drop column campaign_id  (if it still exists)
--   C  – create campaign_bookmarks (IF NOT EXISTS)
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- B1: Drop the FK constraint that references campaigns(campaign_id)
-- ─────────────────────────────────────────────────────────────────────────────
SET @_fk = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = DATABASE()
      AND TABLE_NAME      = 'approvals_log'
      AND CONSTRAINT_NAME = 'fk_al_campaign'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @_sql_fk = IF(@_fk > 0,
    'ALTER TABLE approvals_log DROP FOREIGN KEY fk_al_campaign',
    'SELECT 1'
);
PREPARE _s FROM @_sql_fk; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ─────────────────────────────────────────────────────────────────────────────
-- B2: Now drop the column (safe now that the FK is gone)
-- ─────────────────────────────────────────────────────────────────────────────
SET @_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'approvals_log'
      AND COLUMN_NAME  = 'campaign_id'
);
SET @_sql_col = IF(@_col > 0,
    'ALTER TABLE approvals_log DROP COLUMN campaign_id',
    'SELECT 1'
);
PREPARE _s FROM @_sql_col; EXECUTE _s; DEALLOCATE PREPARE _s;

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
