-- =============================================================================
--  V4 – Notification System
--  Adds three tables:
--    notification_event_types  – registry of all event types
--    notification_templates    – per-event, per-role message + URL templates
--    notifications             – persisted notification per recipient
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- 1. Event-type registry
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_event_types (
    id          BIGINT         AUTO_INCREMENT PRIMARY KEY,
    event_type  VARCHAR(100)   NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 2. Templates  (role_id NULL = default; non-null = role-specific override)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_templates (
    id               BIGINT        AUTO_INCREMENT PRIMARY KEY,
    event_type       VARCHAR(100)  NOT NULL,
    role_id          VARCHAR(20)   NULL COMMENT 'NULL = default for all roles',
    message_template VARCHAR(1000) NOT NULL,
    url_template     VARCHAR(500)  NOT NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_template_event_role (event_type, role_id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- 3. Persisted notifications per user
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    id         BIGINT        AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    event_type VARCHAR(100)  NOT NULL,
    message    VARCHAR(1000) NOT NULL,
    url        VARCHAR(500)  NOT NULL,
    is_read    TINYINT(1)    NOT NULL DEFAULT 0,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notifications_user        (user_id),
    INDEX idx_notifications_user_unread (user_id, is_read)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- Seed event types
-- =============================================================================
INSERT IGNORE INTO notification_event_types (event_type, description) VALUES
  ('TASK_ASSIGNED',             'Fired when a task is assigned to a user'),
  ('ADDED_TO_COLLABORATION',    'Fired when a user is added to a task collaboration'),
  ('NEW_TASK_MESSAGE',          'Fired when a new chat message is posted in a collaboration'),
  ('SUBMITTED_FOR_QC',          'Fired when a worker submits their task for QC review'),
  ('MANAGER_QC_APPROVAL',       'Fired when a manager approves a task during QC'),
  ('REQUESTOR_QC_APPROVAL',     'Fired when a requestor approves a completed task'),
  ('TASK_HELD_BY_MANAGER',      'Fired when a manager places a task on hold'),
  ('TASK_CANCELLED',            'Fired when a manager cancels an assigned task'),
  ('CAMPAIGN_DELETED',          'Fired when a requestor deletes an entire campaign'),
  ('COMMENT_ADDED',             'Fired when a worker adds a comment and self-holds their task'),
  ('COMMENT_RESPONDED',         'Fired when a requestor/manager marks a worker comment as answered'),
  ('MANAGER_REWORK',            'Fired when a manager sends a task back for rework'),
  ('REQUESTOR_REWORK',          'Fired when a requestor sends a task back for rework'),
  ('MANAGER_REJECT',            'Fired when a manager rejects a task during QC'),
  ('CONTENT_TASK_SUBMITTED',    'Fired when a content writer submits their auto-generated content task'),
  ('CONTENT_TASK_AUTO_CLOSED',  'Fired when a content task is auto-completed because the designer submitted for QC');

-- =============================================================================
-- Seed default templates
-- =============================================================================
INSERT IGNORE INTO notification_templates (event_type, role_id, message_template, url_template) VALUES
  ('TASK_ASSIGNED', NULL,
   'A new task {taskId} has been assigned to you',
   '/my-tasks'),

  ('ADDED_TO_COLLABORATION', NULL,
   '{inviterName} added you to collaboration on task {taskId}',
   '/collaborations?taskId={taskId}'),

  ('NEW_TASK_MESSAGE', NULL,
   '{senderName} sent a message in task {taskId}',
   '/collaborations?taskId={taskId}'),

  ('SUBMITTED_FOR_QC', NULL,
   '{workerName} submitted task {taskId} for QC review',
   '/manager/qc-review'),

  -- Default (for worker): awaiting requestor sign-off
  ('MANAGER_QC_APPROVAL', NULL,
   'Manager approved your task {taskId}. Awaiting requestor sign-off.',
   '/my-tasks'),

  -- Requestor role override (role_id 12): redirect to requestor QC page
  ('MANAGER_QC_APPROVAL', '12',
   'Task {taskId} has passed manager QC and is ready for your review',
   '/requestor-qc-review?taskId={taskId}'),

  ('REQUESTOR_QC_APPROVAL', NULL,
   '{requestorName} approved your task {taskId}',
   '/my-tasks'),

  ('TASK_HELD_BY_MANAGER', NULL,
   'Manager {managerName} has held your task {taskId}',
   '/my-tasks'),

  ('TASK_CANCELLED', NULL,
   'Your task {taskId} has been cancelled by {managerName}',
   '/my-tasks'),

  ('CAMPAIGN_DELETED', NULL,
   'Campaign {campaignName} has been deleted. Your assigned task is no longer active.',
   '/my-tasks'),

  ('COMMENT_ADDED', NULL,
   '{workerName} added a comment on task {taskId} — please check it out',
   '/campaigns'),

  ('COMMENT_RESPONDED', NULL,
   '{responderName} responded to your comment on task {taskId}',
   '/my-tasks'),

  ('MANAGER_REWORK', NULL,
   'Manager {managerName} has requested rework on your task {taskId}',
   '/my-tasks'),

  ('REQUESTOR_REWORK', NULL,
   '{requestorName} has requested rework on your task {taskId}',
   '/my-tasks'),

  ('MANAGER_REJECT', NULL,
   'Manager {managerName} has rejected your task {taskId}',
   '/my-tasks'),

  ('CONTENT_TASK_SUBMITTED', NULL,
   '{writerName} has completed the content writing task {taskId} — please review it',
   '/my-tasks'),

  ('CONTENT_TASK_AUTO_CLOSED', NULL,
   'Your content writing task {taskId} has been marked as complete (designer submitted for QC)',
   '/my-tasks');
