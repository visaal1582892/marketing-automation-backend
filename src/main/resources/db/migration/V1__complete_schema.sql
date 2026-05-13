-- =============================================================================
--  V1 – Complete Baseline Schema (single source of truth)
--  ALL schema changes are baked in here so a fresh database requires only
--  this file + V2 (seed data).  All statements use IF NOT EXISTS / IF EXISTS
--  to remain idempotent.  V3-V7 are intentionally absent as separate files;
--  their content lives here.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- MASTER LOOKUP TABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS departments (
    department_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    department_name  VARCHAR(200)              NOT NULL UNIQUE,
    status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
    role_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    role_name  VARCHAR(200)              NOT NULL UNIQUE,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS designations (
    designation_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    designation_name  VARCHAR(200)              NOT NULL UNIQUE,
    status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS requirement_types (
    requirement_type_id  VARCHAR(20)               NOT NULL PRIMARY KEY,
    requirement_name     VARCHAR(200)              NOT NULL UNIQUE,
    status               ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_types (
    task_type_id  VARCHAR(20)               NOT NULL PRIMARY KEY,
    task_name     VARCHAR(200)              NOT NULL UNIQUE,
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS regions (
    region_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    region_name  VARCHAR(200)              NOT NULL UNIQUE,
    status       ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at   TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audiences (
    audience_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    audience_name  VARCHAR(200)              NOT NULL UNIQUE,
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS offer_types (
    offer_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    offer_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS vendor_types (
    vendor_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    vendor_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS business_objectives (
    business_objective_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    business_objective_name  VARCHAR(200)              NOT NULL UNIQUE,
    status                   ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at               TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS languages (
    language_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    language_name  VARCHAR(200)              NOT NULL UNIQUE,
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS tones (
    tone_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    tone_name  VARCHAR(200)              NOT NULL UNIQUE,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS supporting_proofs (
    supporting_proof_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    supporting_proof_name  VARCHAR(200)              NOT NULL UNIQUE,
    status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS budget_tiers (
    budget_tier_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    budget_tier_name  VARCHAR(200)              NOT NULL UNIQUE,
    status            ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kpi_types (
    kpi_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    kpi_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS expected_outputs (
    expected_output_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    expected_output_name  VARCHAR(200)              NOT NULL UNIQUE,
    status                ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =============================================================================
-- GRANULAR TASKS
-- =============================================================================

CREATE TABLE IF NOT EXISTS granular_tasks (
    task_id        VARCHAR(20)               NOT NULL PRIMARY KEY,
    task_name      VARCHAR(255)              NOT NULL UNIQUE,
    task_type_id   VARCHAR(20)               NOT NULL,
    task_category  VARCHAR(100),
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_gt_task_type FOREIGN KEY (task_type_id)
        REFERENCES task_types(task_type_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- =============================================================================
-- ROUTING ENGINE CONFIG
-- =============================================================================

CREATE TABLE IF NOT EXISTS requirement_role_mapping (
    mapping_id           INT                       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requirement_type_id  VARCHAR(20)               NOT NULL,
    default_role_id      VARCHAR(20)               NOT NULL,
    status               ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_req_role (requirement_type_id),
    CONSTRAINT fk_rrm_req  FOREIGN KEY (requirement_type_id)
        REFERENCES requirement_types(requirement_type_id) ON DELETE CASCADE,
    CONSTRAINT fk_rrm_role FOREIGN KEY (default_role_id)
        REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS role_task_mapping (
    mapping_id  INT                       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id     VARCHAR(20)               NOT NULL,
    task_id     VARCHAR(20)               NOT NULL,
    status      ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_role_task (role_id, task_id),
    CONSTRAINT fk_rtm_role FOREIGN KEY (role_id)
        REFERENCES roles(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_rtm_task FOREIGN KEY (task_id)
        REFERENCES granular_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- USERS
-- =============================================================================

CREATE TABLE IF NOT EXISTS users (
    user_id                INT                       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name              VARCHAR(255)              NOT NULL,
    email                  VARCHAR(255)              NOT NULL UNIQUE,
    password_hash          VARCHAR(255)              NOT NULL,
    department_id          VARCHAR(20),
    designation_id         VARCHAR(20),
    skill_level            ENUM('SENIOR','JUNIOR','INTERN'),
    current_active_tasks   INT                       DEFAULT 0,
    status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP                 DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP                 DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_dept        FOREIGN KEY (department_id)   REFERENCES departments(department_id) ON DELETE CASCADE,
    CONSTRAINT fk_users_designation FOREIGN KEY (designation_id)  REFERENCES designations(designation_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- USER ROLES  (junction table — many roles per user)
-- =============================================================================

CREATE TABLE IF NOT EXISTS user_roles (
    user_id INT         NOT NULL,
    role_id VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- CAMPAIGNS
-- requirement_type_id removed (replaced by task_type_id JSON array — V5+V7).
-- =============================================================================

CREATE TABLE IF NOT EXISTS campaigns (
    campaign_id               INT                        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requestor_id              INT                        NOT NULL,
    department_id             VARCHAR(20),
    target_location           TEXT,
    business_objective        VARCHAR(200),
    task_type_id              VARCHAR(500)               NULL,
    audience_type_id          VARCHAR(500),
    language                  VARCHAR(500),
    has_offer                 VARCHAR(3)                 NOT NULL DEFAULT 'NO',
    offer_type_id             VARCHAR(200),
    key_message               VARCHAR(500),
    supporting_proof          VARCHAR(200),
    tone                      VARCHAR(500),
    priority                  ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    budget_tier               VARCHAR(200),
    vendor_required           VARCHAR(3)                 NOT NULL DEFAULT 'NO',
    vendor_type               VARCHAR(500),
    kpi_type                  VARCHAR(200),
    expected_output           VARCHAR(200),
    deadline                  DATE,
    status                    ENUM(
                                  'IN_PROGRESS',
                                  'QC_REVIEW',
                                  'COMPLETED',
                                  'REJECTED',
                                  'CANCELLED'
                              )                         NOT NULL DEFAULT 'IN_PROGRESS',
    routing_notes             VARCHAR(1000),
    flagged_inconsistency     TINYINT(1)                 NOT NULL DEFAULT 0,
    inconsistency_reason      VARCHAR(500),
    rejection_reason          VARCHAR(1000),
    created_at                TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
    updated_at                TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_camp_requestor FOREIGN KEY (requestor_id)   REFERENCES users(user_id),
    CONSTRAINT fk_camp_dept      FOREIGN KEY (department_id)  REFERENCES departments(department_id)
) ENGINE=InnoDB;

-- =============================================================================
-- CAMPAIGN DELIVERABLES
-- =============================================================================

CREATE TABLE IF NOT EXISTS campaign_deliverables (
    spec_id           INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id       INT         NOT NULL,
    granular_task_id  VARCHAR(20) NOT NULL,
    CONSTRAINT fk_cd_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns(campaign_id) ON DELETE CASCADE,
    CONSTRAINT fk_cd_task     FOREIGN KEY (granular_task_id)
        REFERENCES granular_tasks(task_id)
) ENGINE=InnoDB;

-- =============================================================================
-- WORK TASKS
-- collaboration_chat_paused removed; replaced by two precise flags (V3+V4).
-- is_collaboration_started: sticky flag set when worker clicks "Collaborate".
-- is_collaboration_active:  true when chat/uploads are currently open.
-- =============================================================================

CREATE TABLE IF NOT EXISTS work_tasks (
    task_id                    VARCHAR(20)   NOT NULL PRIMARY KEY,
    campaign_id                INT           NOT NULL,
    assigned_to                INT,
    granular_task_id           VARCHAR(20),
    status                     ENUM(
                                   'ASSIGNED','ACCEPTED','IN_PROGRESS',
                                   'QC_REVIEW','REWORK','COMPLETED',
                                   'HELD','CANCELLED','REJECTED'
                               )             NOT NULL DEFAULT 'ASSIGNED',
    pre_hold_status            VARCHAR(20)   NULL,
    assigned_at                TIMESTAMP     NULL,
    accepted_at                TIMESTAMP     NULL,
    started_at                 TIMESTAMP     NULL,
    submitted_at               TIMESTAMP     NULL,
    completed_at               TIMESTAMP     NULL,
    total_time_logged_minutes  INT           DEFAULT 0,
    dynamic_deadline           TIMESTAMP     NULL,
    submission_notes           TEXT,
    rework_count               INT           DEFAULT 0,
    is_collaboration_started   BOOLEAN       NOT NULL DEFAULT FALSE,
    is_collaboration_active    BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wt_campaign  FOREIGN KEY (campaign_id)      REFERENCES campaigns(campaign_id)   ON DELETE CASCADE,
    CONSTRAINT fk_wt_user      FOREIGN KEY (assigned_to)      REFERENCES users(user_id),
    CONSTRAINT fk_wt_gran_task FOREIGN KEY (granular_task_id) REFERENCES granular_tasks(task_id)
) ENGINE=InnoDB;

-- =============================================================================
-- WORKER COMMENTS
-- =============================================================================

CREATE TABLE IF NOT EXISTS worker_comments (
    comment_id  INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id     VARCHAR(20)  NOT NULL,
    user_id     INT          NOT NULL,
    comment     TEXT         NOT NULL,
    is_answered TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wc_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_wc_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- =============================================================================
-- DYNAMIC QUESTIONS
-- =============================================================================

CREATE TABLE IF NOT EXISTS dynamic_questions (
    question_id   VARCHAR(20)                              NOT NULL PRIMARY KEY,
    question_text VARCHAR(1000)                            NOT NULL,
    field_type    ENUM('TEXT','NUMBER','TEXTAREA',
                       'DROPDOWN','MULTISELECT','DATE',
                       'FILE','CHECKBOX')                  NOT NULL DEFAULT 'TEXT',
    options       JSON,
    is_required   TINYINT(1)                              NOT NULL DEFAULT 0,
    created_at    TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP                               NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_question_mapping (
    granular_task_id  VARCHAR(20) NOT NULL,
    question_id       VARCHAR(20) NOT NULL,
    PRIMARY KEY (granular_task_id, question_id),
    CONSTRAINT fk_tqm_task     FOREIGN KEY (granular_task_id) REFERENCES granular_tasks(task_id)     ON DELETE CASCADE,
    CONSTRAINT fk_tqm_question FOREIGN KEY (question_id)      REFERENCES dynamic_questions(question_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS work_task_answers (
    answer_id     VARCHAR(20)  NOT NULL PRIMARY KEY,
    work_task_id  VARCHAR(20)  NOT NULL,
    question_id   VARCHAR(20)  NOT NULL,
    answer_value  TEXT,
    UNIQUE KEY uq_wta_task_question (work_task_id, question_id),
    CONSTRAINT fk_wta_task     FOREIGN KEY (work_task_id) REFERENCES work_tasks(task_id)            ON DELETE CASCADE,
    CONSTRAINT fk_wta_question FOREIGN KEY (question_id)  REFERENCES dynamic_questions(question_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- APPROVALS LOG
-- action_taken ENUM includes HELD and UNHOLD (V8).
-- =============================================================================

CREATE TABLE IF NOT EXISTS approvals_log (
    log_id        INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id       VARCHAR(20)  NOT NULL,
    reviewer_id   INT          NOT NULL,
    action_taken  ENUM('APPROVED','NEEDS_REWORK','REJECTED','REQUESTOR_REWORK','HELD','UNHOLD') NOT NULL,
    comments      TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_al_task     FOREIGN KEY (task_id)     REFERENCES work_tasks(task_id)  ON DELETE CASCADE,
    CONSTRAINT fk_al_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- =============================================================================
-- CAMPAIGN FILES
-- =============================================================================

CREATE TABLE IF NOT EXISTS campaign_files (
    file_id      INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id  INT          NOT NULL,
    file_url     TEXT         NOT NULL,
    file_name    VARCHAR(500),
    uploaded_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cf_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns(campaign_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- CAMPAIGN BOOKMARKS
-- =============================================================================

CREATE TABLE IF NOT EXISTS campaign_bookmarks (
    user_id     INT      NOT NULL,
    campaign_id INT      NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, campaign_id),
    CONSTRAINT fk_bm_user     FOREIGN KEY (user_id)     REFERENCES users(user_id)     ON DELETE CASCADE,
    CONSTRAINT fk_bm_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(campaign_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- LEAD QUALITY METRICS
-- =============================================================================

CREATE TABLE IF NOT EXISTS lead_quality_metrics (
    lead_id                INT              NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id            INT              NOT NULL,
    crm_lead_reference_id  VARCHAR(255)     UNIQUE,
    lead_status            ENUM('HOT','WARM','COLD'),
    is_converted           ENUM('YES','NO') NOT NULL DEFAULT 'NO',
    revenue_generated      DECIMAL(10,2)    DEFAULT 0.00,
    generated_at           TIMESTAMP        NULL,
    status                 ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    updated_at             TIMESTAMP        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_lqm_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(campaign_id)
) ENGINE=InnoDB;

-- =============================================================================
-- TASK COLLABORATORS
-- =============================================================================

CREATE TABLE IF NOT EXISTS task_collaborators (
    id        INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id   VARCHAR(20)  NOT NULL,
    user_id   INT          NOT NULL,
    added_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_tc (task_id, user_id),
    CONSTRAINT fk_tc_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_tc_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- =============================================================================
-- TASK MESSAGES
-- =============================================================================

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

-- =============================================================================
-- ASSET INFO
-- =============================================================================

CREATE TABLE IF NOT EXISTS asset_info (
    asset_id          INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id           VARCHAR(20)  NOT NULL,
    user_id           INT          NOT NULL,
    url               TEXT         NOT NULL,
    thumbnail_url     TEXT         NULL,
    original_filename VARCHAR(500) NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ai_task FOREIGN KEY (task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_ai_user FOREIGN KEY (user_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- =============================================================================
-- AUTO-CREATED TASKS  (designer → content-writer handoff)
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
    UNIQUE KEY uk_auto_created_child  (created_task_id),
    CONSTRAINT fk_auto_created_source FOREIGN KEY (source_task_id)  REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_auto_created_child  FOREIGN KEY (created_task_id) REFERENCES work_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =============================================================================
-- QC ROUTING  (maps worker roles to manager roles for QC queue filtering)
-- =============================================================================

CREATE TABLE IF NOT EXISTS qc_routing (
    id              INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    worker_role_id  VARCHAR(20)  NOT NULL,
    manager_role_id VARCHAR(20)  NOT NULL,
    UNIQUE KEY uq_qc_routing (worker_role_id, manager_role_id),
    CONSTRAINT fk_qcr_worker  FOREIGN KEY (worker_role_id)  REFERENCES roles(role_id),
    CONSTRAINT fk_qcr_manager FOREIGN KEY (manager_role_id) REFERENCES roles(role_id)
) ENGINE=InnoDB;

SET FOREIGN_KEY_CHECKS = 1;
