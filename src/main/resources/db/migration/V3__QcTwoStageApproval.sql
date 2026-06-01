-- =============================================================================
--  V3 – Two-stage QC approval workflow (idempotent)
--  V1 already has these changes baked in for fresh databases.
--  This migration resolves the version gap for existing V1+V2 databases.
-- =============================================================================

-- Extend campaigns.status ENUM to include two-stage QC values
ALTER TABLE campaigns
    MODIFY COLUMN status
    ENUM('IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REQUESTOR_QC_REVIEW',
         'COMPLETED','REJECTED','CANCELLED')
    NOT NULL DEFAULT 'IN_PROGRESS';

-- Migrate any legacy QC_REVIEW rows
UPDATE campaigns SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW';

-- Extend work_tasks.status ENUM
ALTER TABLE work_tasks
    MODIFY COLUMN status
    ENUM('ASSIGNED','ACCEPTED','IN_PROGRESS','QC_REVIEW','MANAGER_QC_REVIEW','REWORK',
         'REQUESTOR_QC_REVIEW','COMPLETED','HELD','CANCELLED','REJECTED')
    NOT NULL DEFAULT 'ASSIGNED';

-- Migrate any legacy QC_REVIEW rows
UPDATE work_tasks SET status = 'MANAGER_QC_REVIEW' WHERE status = 'QC_REVIEW';

-- Add manager_approved_at and requestor_approved_at.
-- On a clean database these columns do not yet exist (V1 predates this feature).
-- Flyway prevents this script from running twice, so IF NOT EXISTS is not needed
-- (and is not valid MySQL 8.0 syntax — it is MariaDB-only).
ALTER TABLE work_tasks
    ADD COLUMN manager_approved_at DATETIME NULL DEFAULT NULL;

ALTER TABLE work_tasks
    ADD COLUMN requestor_approved_at DATETIME NULL DEFAULT NULL;
