-- V8: Extend approvals_log to track hold/unhold events.
--
-- Widen the action_taken ENUM to include HELD and UNHOLD.
-- reviewer_id (already present) records who performed the action.
-- No new column is needed.

ALTER TABLE approvals_log
    MODIFY COLUMN action_taken
        ENUM('APPROVED','NEEDS_REWORK','REJECTED','REQUESTOR_REWORK','HELD','UNHOLD') NOT NULL;
