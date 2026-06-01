-- =============================================================================
--  V5 – Notification Status & Reference
--  Adds two columns to the notifications table:
--    reference_id  – taskId / campaignId that the notification is about
--    status        – 1 = active (action not yet taken), 0 = resolved
-- =============================================================================

-- ADD COLUMN IF NOT EXISTS is MariaDB syntax and is not supported by MySQL 8.0.
-- Flyway prevents this script from running twice, so the guards are unnecessary.
ALTER TABLE notifications
    ADD COLUMN reference_id VARCHAR(50)   NULL COMMENT 'taskId or campaignId this notification relates to',
    ADD COLUMN status       TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '1=active, 0=resolved';

-- Index for fast resolve queries (CREATE INDEX IF NOT EXISTS is also not valid MySQL syntax)
CREATE INDEX idx_notif_reference ON notifications (reference_id, event_type, status);
