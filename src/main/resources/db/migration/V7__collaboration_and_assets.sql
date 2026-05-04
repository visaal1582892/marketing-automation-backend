-- =============================================================================
-- V7: Collaboration, Chat, and Asset Info tables
--
-- 1. Create task_collaborators  — tracks who was invited to collaborate
-- 2. Create task_messages       — per-task real-time chat log
-- 3. Create asset_info          — replaces work_tasks.asset_url
-- 4. Drop work_tasks.asset_url  (idempotent via IF EXISTS – MySQL 8.0.29+)
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. task_collaborators
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS task_collaborators (
    id        INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id   VARCHAR(20)  NOT NULL,
    user_id   INT          NOT NULL,
    added_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_tc (task_id, user_id),
    CONSTRAINT fk_tc_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_tc_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. task_messages
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS task_messages (
    message_id  INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id     VARCHAR(20)  NOT NULL,
    user_id     INT          NOT NULL,
    message     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tm_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. asset_info  (replaces work_tasks.asset_url)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS asset_info (
    asset_id    INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id     VARCHAR(20)  NOT NULL,
    user_id     INT          NOT NULL,
    url         TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Drop work_tasks.asset_url
--    MySQL 8.0.29+ supports DROP COLUMN IF EXISTS — safe to re-run.
-- ─────────────────────────────────────────────────────────────────────────────
ALTER TABLE work_tasks DROP COLUMN IF EXISTS asset_url;
