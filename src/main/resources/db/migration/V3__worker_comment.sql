-- V3: Worker comment & self-hold
-- Allows a worker to leave a comment on their task and pause it (HELD)
-- until the requestor addresses the query.
-- Idempotent: only adds the column when it does not already exist.

SET @_col = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME   = 'work_tasks'
      AND COLUMN_NAME  = 'worker_comment'
);
SET @_sql = IF(@_col = 0,
    'ALTER TABLE work_tasks ADD COLUMN worker_comment VARCHAR(1000) NULL AFTER submission_notes',
    'SELECT 1'
);
PREPARE _s FROM @_sql; EXECUTE _s; DEALLOCATE PREPARE _s;
