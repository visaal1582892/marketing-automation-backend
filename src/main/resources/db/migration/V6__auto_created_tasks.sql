-- =============================================================================
--  V6 – Auto-created content tasks (designer → content writer handoff)
-- =============================================================================

CREATE TABLE IF NOT EXISTS auto_created_tasks (
    auto_created_task_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    source_task_id           VARCHAR(32)     NOT NULL,
    created_task_id          VARCHAR(32)     NOT NULL,
    campaign_id              INT             NOT NULL,
    source_granular_task_id  VARCHAR(20)     NULL,
    content_granular_task_id VARCHAR(20)     NOT NULL,
    requested_by_user_id     INT UNSIGNED    NOT NULL,
    content_assignee_user_id INT UNSIGNED    NULL,
    status                   ENUM('REQUESTED','IN_PROGRESS','COMPLETED','CANCELLED') NOT NULL DEFAULT 'REQUESTED',
    created_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_auto_created_source (source_task_id),
    UNIQUE KEY uk_auto_created_child (created_task_id),
    CONSTRAINT fk_auto_created_source FOREIGN KEY (source_task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_auto_created_child  FOREIGN KEY (created_task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB;
