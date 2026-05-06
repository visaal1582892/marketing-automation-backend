-- Adds a per-task flag that lets the collaboration owner pause the chat
-- without changing the task's work status.
-- When TRUE: chat is blocked for all participants, but assets remain uploadable.
ALTER TABLE work_tasks
  ADD COLUMN collaboration_chat_paused BOOLEAN NOT NULL DEFAULT FALSE;
