-- ─── V4: Replace collaboration_chat_paused with two precise boolean columns ──
--
-- is_collaboration_started : true once the worker clicks "Collaborate".
--                            Never reverts to false — it is a sticky flag.
-- is_collaboration_active  : true when collaboration is currently open for
--                            chat and asset uploads; false when the task is
--                            held, in QC, completed, or the owner has manually
--                            paused the chat.

ALTER TABLE work_tasks
  ADD COLUMN is_collaboration_started BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN is_collaboration_active  BOOLEAN NOT NULL DEFAULT FALSE;

-- Step 1: Mark tasks where collaboration has already been initiated.
--         Any task that has at least one row in task_collaborators was
--         explicitly started by the worker clicking "Collaborate".
UPDATE work_tasks
SET    is_collaboration_started = 1
WHERE  task_id IN (SELECT DISTINCT task_id FROM task_collaborators);

-- Step 2: Mark the collaboration as currently active when ALL of:
--   a) collaboration was started (step 1)
--   b) task is in a working status where chat/assets should be open
--   c) the old manual-pause flag was NOT set (preserve intentional pauses)
UPDATE work_tasks
SET    is_collaboration_active = 1
WHERE  is_collaboration_started = 1
  AND  status IN ('IN_PROGRESS', 'REWORK', 'REQUESTOR_REWORK')
  AND  collaboration_chat_paused = 0;

-- Step 3: Drop the old single-column flag — its meaning is now fully
--         captured by the combination of is_collaboration_active and task status.
ALTER TABLE work_tasks DROP COLUMN collaboration_chat_paused;
