-- V5: Add task_type_id column to campaigns table.
-- Existing rows keep requirement_type_id as-is; new campaigns will populate task_type_id.
-- No data is lost or migrated — the column is nullable to stay backward-compatible.
-- Uses IF NOT EXISTS (MySQL 8.0+) so the migration is safe to re-run.

ALTER TABLE campaigns
    ADD COLUMN task_type_id VARCHAR(500) NULL AFTER requirement_type_id;
