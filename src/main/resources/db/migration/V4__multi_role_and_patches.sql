-- =============================================================================
-- V4: Multi-role system + remaining patches  (idempotent via information_schema)
--
-- Uses SET + PREPARE instead of DDL "IF EXISTS" clauses so this script works
-- on both MySQL 5.7 and 8.x.  Safe to re-run: every step checks state first.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- A1: Remove duplicate work_task_answers rows
-- ─────────────────────────────────────────────────────────────────────────────
DELETE wta
  FROM work_task_answers wta
 INNER JOIN work_task_answers wta2
         ON  wta.work_task_id = wta2.work_task_id
         AND wta.question_id  = wta2.question_id
         AND CAST(SUBSTRING(wta.answer_id, 5) AS UNSIGNED)
           < CAST(SUBSTRING(wta2.answer_id, 5) AS UNSIGNED);

-- A2: Add unique constraint only when it does not already exist
SET @_idx = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'work_task_answers'
      AND INDEX_NAME   = 'uq_wta_task_question'
);
SET @_sql = IF(@_idx = 0,
    'ALTER TABLE work_task_answers ADD CONSTRAINT uq_wta_task_question UNIQUE (work_task_id, question_id)',
    'SELECT 1'
);
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ─────────────────────────────────────────────────────────────────────────────
-- B: user_roles junction table
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_roles (
    user_id INT         NOT NULL,
    role_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- Seed from users.role_id — only if that column still exists
SET @_has_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'users'
      AND COLUMN_NAME  = 'role_id'
);
SET @_seed = IF(@_has_col > 0,
    'INSERT IGNORE INTO user_roles (user_id, role_id) SELECT user_id, role_id FROM users WHERE role_id IS NOT NULL',
    'SELECT 1'
);
PREPARE _s FROM @_seed; EXECUTE _s; DEALLOCATE PREPARE _s;

-- ─────────────────────────────────────────────────────────────────────────────
-- C: Drop users.role_id (data is now in user_roles)
-- ─────────────────────────────────────────────────────────────────────────────

-- Drop FK first (if it still exists)
SET @_fk = (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE TABLE_SCHEMA    = DATABASE()
      AND TABLE_NAME      = 'users'
      AND CONSTRAINT_NAME = 'fk_users_role'
      AND CONSTRAINT_TYPE = 'FOREIGN KEY'
);
SET @_fk_sql = IF(@_fk > 0, 'ALTER TABLE users DROP FOREIGN KEY fk_users_role', 'SELECT 1');
PREPARE _s FROM @_fk_sql; EXECUTE _s; DEALLOCATE PREPARE _s;

-- Drop the column (if it still exists)
SET @_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'users'
      AND COLUMN_NAME  = 'role_id'
);
SET @_col_sql = IF(@_col > 0, 'ALTER TABLE users DROP COLUMN role_id', 'SELECT 1');
PREPARE _s FROM @_col_sql; EXECUTE _s; DEALLOCATE PREPARE _s;
