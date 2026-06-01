-- V8 — Remove task_type_id from campaigns table
-- task_type is now derived per-task from granular_tasks.task_type_id
-- The Task Type field in forms is used only as a UI filter, not persisted on the campaign.

ALTER TABLE campaigns DROP COLUMN task_type_id;
