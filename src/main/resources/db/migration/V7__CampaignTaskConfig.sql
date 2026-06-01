-- V7 — Campaign Task Configuration
-- Stores which granular tasks apply for a given combination of
-- campaign type + business vertical + business type + store format type.
--
-- Design:
--   • Each DB row = one task for one combination.
--   • Multiple tasks for the same combination = multiple rows.
--   • UNIQUE KEY on all four spec columns + granular_task_id:
--       - the same (combination, task) pair cannot be duplicated
--       - different tasks for the same combination are allowed (separate rows)
--   • Empty string '' is used for "not specified" so MySQL UNIQUE works
--     correctly (NULL values are NOT considered equal in MySQL UNIQUE indexes).

CREATE TABLE IF NOT EXISTS campaign_task_config (
    id                   BIGINT UNSIGNED               NOT NULL AUTO_INCREMENT,
    campaign_type_id     VARCHAR(20)                   NOT NULL DEFAULT '',
    business_vertical_id VARCHAR(20)                   NOT NULL DEFAULT '',
    business_type_id     VARCHAR(20)                   NOT NULL DEFAULT '',
    store_format_type_id VARCHAR(20)                   NOT NULL DEFAULT '',
    granular_task_id     VARCHAR(20)                   NOT NULL,
    status               ENUM('ACTIVE', 'INACTIVE')    NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP                     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),

    -- Unique per (combination, task) — prevents duplicate task rows
    UNIQUE KEY uq_ctc_combo_task (
        campaign_type_id,
        business_vertical_id,
        business_type_id,
        store_format_type_id,
        granular_task_id
    ),

    CONSTRAINT fk_ctc_task
        FOREIGN KEY (granular_task_id)
        REFERENCES granular_tasks (task_id)
        ON DELETE CASCADE

) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
