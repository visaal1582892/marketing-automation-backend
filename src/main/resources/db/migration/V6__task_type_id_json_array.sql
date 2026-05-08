-- V6: Convert task_type_id to a JSON-array field (matching the pattern used by
--     audience_type_id, language, tone, vendor_type, etc.).
--
-- Migration strategy for existing campaigns:
--   • Campaigns that already have a task_type_id set (from V5 single-value era)
--     → wrap that value in a JSON array so the format is consistent.
--   • Campaigns that only have a legacy requirement_type_id (no task_type_id yet)
--     → wrap the requirement_type_id in a JSON array and use it as task_type_id.
--     This preserves the old data as best-effort; the display falls back to
--     requirementTypeName when the ID doesn't resolve in task_types.
--   • Campaigns where task_type_id is already a valid JSON array → leave untouched.

-- 1. For rows where task_type_id is a plain non-JSON value (not starting with '['),
--    convert to JSON array wrapping the existing value.
UPDATE campaigns
SET task_type_id = JSON_ARRAY(task_type_id)
WHERE task_type_id IS NOT NULL
  AND task_type_id != ''
  AND LEFT(TRIM(task_type_id), 1) != '[';

-- 2. For rows still without a task_type_id but with a legacy requirement_type_id,
--    wrap the requirement_type_id as a JSON array into task_type_id.
UPDATE campaigns
SET task_type_id = JSON_ARRAY(requirement_type_id)
WHERE (task_type_id IS NULL OR task_type_id = '')
  AND requirement_type_id IS NOT NULL
  AND requirement_type_id != '';
