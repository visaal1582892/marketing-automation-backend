-- V7: Remove the legacy requirement_type_id column from campaigns.
-- task_type_id (JSON array) is now the sole source of campaign type data.
-- requirement_types master table and requirement_role_mapping config table are
-- intentionally kept — they are used by the routing-config admin screen.

ALTER TABLE campaigns DROP COLUMN IF EXISTS requirement_type_id;
