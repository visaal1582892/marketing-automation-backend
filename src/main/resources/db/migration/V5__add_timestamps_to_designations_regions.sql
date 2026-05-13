-- =============================================================================
--  V3 – Back-fill created_at / updated_at on designations and regions
--
--  V1 was later updated to include these columns in the CREATE TABLE
--  statements, but databases that were created from the original V1 are
--  missing them.  This migration adds them safely (idempotent) so that:
--    • existing DBs get the columns added and populated;
--    • fresh DBs (where V1 already created the columns) skip the ALTER.
--
--  MySQL 8 does NOT support "ADD COLUMN IF NOT EXISTS", so we use a
--  prepared-statement trick against information_schema for idempotency.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ─── designations.created_at ─────────────────────────────────────────────────
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'designations'
       AND COLUMN_NAME  = 'created_at') > 0,
    'SELECT 1 /* designations.created_at already exists – skipping */',
    'ALTER TABLE designations ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP'
);
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ─── designations.updated_at ─────────────────────────────────────────────────
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'designations'
       AND COLUMN_NAME  = 'updated_at') > 0,
    'SELECT 1 /* designations.updated_at already exists – skipping */',
    'ALTER TABLE designations ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
);
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ─── regions.created_at ──────────────────────────────────────────────────────
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'regions'
       AND COLUMN_NAME  = 'created_at') > 0,
    'SELECT 1 /* regions.created_at already exists – skipping */',
    'ALTER TABLE regions ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP'
);
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ─── regions.updated_at ──────────────────────────────────────────────────────
SET @s = IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME   = 'regions'
       AND COLUMN_NAME  = 'updated_at') > 0,
    'SELECT 1 /* regions.updated_at already exists – skipping */',
    'ALTER TABLE regions ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP'
);
PREPARE _stmt FROM @s; EXECUTE _stmt; DEALLOCATE PREPARE _stmt;

-- ─── Seed realistic timestamps for the rows that V2 inserted ─────────────────
-- Each designation / region gets a unique offset so the default
-- "ORDER BY COALESCE(updated_at, created_at) DESC" ordering is deterministic.
-- These UPDATEs only touch rows where the timestamp is still at the ALTER-time
-- default (i.e. all rows, since none existed before the ALTER).
-- Using CASE so the whole block is a single idempotent statement.

UPDATE designations
SET    created_at = CASE designation_id
         WHEN '1'  THEN '2024-01-15 10:00:01'
         WHEN '2'  THEN '2024-01-15 10:00:02'
         WHEN '3'  THEN '2024-01-15 10:00:03'
         WHEN '4'  THEN '2024-01-15 10:00:04'
         WHEN '5'  THEN '2024-01-15 10:00:05'
         WHEN '6'  THEN '2024-01-15 10:00:06'
         WHEN '7'  THEN '2024-01-15 10:00:07'
         WHEN '8'  THEN '2024-01-15 10:00:08'
         WHEN '9'  THEN '2024-01-15 10:00:09'
         ELSE            '2024-01-15 10:00:00'
       END,
       updated_at = CASE designation_id
         WHEN '1'  THEN '2024-01-15 10:00:01'
         WHEN '2'  THEN '2024-01-15 10:00:02'
         WHEN '3'  THEN '2024-01-15 10:00:03'
         WHEN '4'  THEN '2024-01-15 10:00:04'
         WHEN '5'  THEN '2024-01-15 10:00:05'
         WHEN '6'  THEN '2024-01-15 10:00:06'
         WHEN '7'  THEN '2024-01-15 10:00:07'
         WHEN '8'  THEN '2024-01-15 10:00:08'
         WHEN '9'  THEN '2024-01-15 10:00:09'
         ELSE            '2024-01-15 10:00:00'
       END;

UPDATE regions
SET    created_at = CASE region_id
         WHEN '1'  THEN '2024-01-15 10:00:01'
         WHEN '2'  THEN '2024-01-15 10:00:02'
         WHEN '3'  THEN '2024-01-15 10:00:03'
         WHEN '4'  THEN '2024-01-15 10:00:04'
         WHEN '5'  THEN '2024-01-15 10:00:05'
         WHEN '6'  THEN '2024-01-15 10:00:06'
         WHEN '7'  THEN '2024-01-15 10:00:07'
         WHEN '8'  THEN '2024-01-15 10:00:08'
         WHEN '9'  THEN '2024-01-15 10:00:09'
         WHEN '10' THEN '2024-01-15 10:00:10'
         WHEN '11' THEN '2024-01-15 10:00:11'
         WHEN '12' THEN '2024-01-15 10:00:12'
         WHEN '13' THEN '2024-01-15 10:00:13'
         ELSE            '2024-01-15 10:00:00'
       END,
       updated_at = CASE region_id
         WHEN '1'  THEN '2024-01-15 10:00:01'
         WHEN '2'  THEN '2024-01-15 10:00:02'
         WHEN '3'  THEN '2024-01-15 10:00:03'
         WHEN '4'  THEN '2024-01-15 10:00:04'
         WHEN '5'  THEN '2024-01-15 10:00:05'
         WHEN '6'  THEN '2024-01-15 10:00:06'
         WHEN '7'  THEN '2024-01-15 10:00:07'
         WHEN '8'  THEN '2024-01-15 10:00:08'
         WHEN '9'  THEN '2024-01-15 10:00:09'
         WHEN '10' THEN '2024-01-15 10:00:10'
         WHEN '11' THEN '2024-01-15 10:00:11'
         WHEN '12' THEN '2024-01-15 10:00:12'
         WHEN '13' THEN '2024-01-15 10:00:13'
         ELSE            '2024-01-15 10:00:00'
       END;

SET FOREIGN_KEY_CHECKS = 1;
