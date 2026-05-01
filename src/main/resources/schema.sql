-- =====================================================================
--  Marketing Automation System — MySQL Schema  (REFERENCE ONLY)
--
--  This file is kept as documentation only.
--  Schema is now managed by Flyway migrations in db/migration/.
--  Do NOT set spring.sql.init.mode=always — Flyway handles all DDL.
-- =====================================================================

-- =====================================================================
-- 8.1  Master Lookup & Configuration Tables
--   • <name>_id  VARCHAR(20) PK           ← custom id ('ROLE-1', 'DEPT-3')
--   • <name>     VARCHAR(100)             ← display label
--   • status     ENUM('ACTIVE','INACTIVE') ← unified visibility/delete flag
-- =====================================================================

CREATE TABLE IF NOT EXISTS departments (
    department_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    department_name  VARCHAR(100)              NOT NULL UNIQUE,
    status           ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS roles (
    role_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    role_name  VARCHAR(100)              NOT NULL UNIQUE,
    status     ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS requirement_types (
    requirement_type_id  VARCHAR(20)               NOT NULL PRIMARY KEY,
    requirement_name     VARCHAR(100)              NOT NULL UNIQUE,
    status               ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS task_types (
    task_type_id  VARCHAR(20)               NOT NULL PRIMARY KEY,
    task_name     VARCHAR(100)              NOT NULL UNIQUE,
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS regions (
    region_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    region_name  VARCHAR(100)              NOT NULL UNIQUE,
    status       ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audiences (
    audience_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    audience_name  VARCHAR(100)              NOT NULL UNIQUE,
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS platforms (
    platform_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    platform_name  VARCHAR(100)              NOT NULL UNIQUE,
    status         ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS creative_formats (
    format_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    format_name  VARCHAR(100)              NOT NULL UNIQUE,
    status       ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE'
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Granular Tasks  (config — public id 'TASK-1', 'TASK-2', …)
-- Admin-managed via /api/master/granular-tasks
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS granular_tasks (
    task_id       VARCHAR(20)               NOT NULL PRIMARY KEY,
    task_name     VARCHAR(255)              NOT NULL UNIQUE,
    task_type_id  VARCHAR(20)               NOT NULL,
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_gt_task_type FOREIGN KEY (task_type_id) REFERENCES task_types(task_type_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- Routing Engine Config Tables
-- Admin-managed via /api/master/routing
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS requirement_role_mapping (
    mapping_id           INT                       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requirement_type_id  VARCHAR(20)               NOT NULL,
    default_role_id      VARCHAR(20)               NOT NULL,
    status               ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_req_role (requirement_type_id),
    CONSTRAINT fk_rrm_req  FOREIGN KEY (requirement_type_id) REFERENCES requirement_types(requirement_type_id) ON DELETE CASCADE,
    CONSTRAINT fk_rrm_role FOREIGN KEY (default_role_id)     REFERENCES roles(role_id)                        ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS role_task_mapping (
    mapping_id  INT                       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_id     VARCHAR(20)               NOT NULL,
    task_id     VARCHAR(20)               NOT NULL,
    status      ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_role_task (role_id, task_id),
    CONSTRAINT fk_rtm_role FOREIGN KEY (role_id) REFERENCES roles(role_id)          ON DELETE CASCADE,
    CONSTRAINT fk_rtm_task FOREIGN KEY (task_id) REFERENCES granular_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- 8.2  Users & Staff
-- =====================================================================
CREATE TABLE IF NOT EXISTS users (
    user_id                INT                                 NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name              VARCHAR(255)                        NOT NULL,
    email                  VARCHAR(255)                        NOT NULL UNIQUE,
    password_hash          VARCHAR(255)                        NOT NULL,
    department_id          VARCHAR(20),
    role_id                VARCHAR(20),
    skill_level            ENUM('SENIOR','JUNIOR','INTERN'),
    current_active_tasks   INT                                 DEFAULT 0,
    status                 ENUM('ACTIVE','INACTIVE')           NOT NULL DEFAULT 'ACTIVE',
    created_at             TIMESTAMP                           DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_dept FOREIGN KEY (department_id) REFERENCES departments(department_id),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id)       REFERENCES roles(role_id)
) ENGINE=InnoDB;

-- =====================================================================
-- 8.3  Campaign Briefs / Requests
-- =====================================================================
CREATE TABLE IF NOT EXISTS campaigns (
    campaign_id          INT                        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    requestor_id         INT                        NOT NULL,

    -- Section 1: Requestor Details
    department_id        VARCHAR(20),
    target_region_id     VARCHAR(20),
    target_city          VARCHAR(100),
    -- target_location stores the multi-select JSON array of city names that
    -- the requestor enters in the Smart Form (TEXT to fit large selections).
    target_location      TEXT,
    business_objective   ENUM(
                             'LEAD_GENERATION','WALK_INS_FOOTFALL','BRAND_AWARENESS',
                             'PRODUCT_LAUNCH','OFFER_PROMOTION','RECRUITMENT',
                             'INTERNAL_COMMUNICATION','OPERATIONAL_COMPLIANCE_SAFETY'
                         ),

    -- Section 2: Campaign Type
    requirement_type_id  VARCHAR(20),

    -- Section 3: Audience
    audience_type_id     VARCHAR(20),
    geography_tier       ENUM('TIER_1_CITIES','TIER_2_CITIES','TIER_3_RURAL'),
    language             ENUM('ENGLISH','HINDI','TELUGU','TAMIL','KANNADA','MULTI_LANGUAGE'),

    -- Section 4: Offer & Messaging
    has_offer            ENUM('YES','NO')           NOT NULL DEFAULT 'NO',
    offer_type           ENUM(
                             'DISCOUNT_PERCENT','FLAT_DISCOUNT','FREE_CHECKUP',
                             'BUNDLE_OFFER','FRANCHISE_ROI_PITCH','SALARY_HIRING_OFFER'
                         ),
    -- key_message is a free-form short string in current code (the original
    -- ENUM was replaced when the requestor form moved to free-text input).
    key_message          VARCHAR(500),
    supporting_proof     ENUM(
                             'STORE_COUNT','CUSTOMER_BASE','YEARS_IN_MARKET',
                             'DOCTOR_RECOMMENDATION','TESTIMONIALS_AVAILABLE'
                         ),

    -- Section 5: Deliverables & Specs
    tone                 ENUM(
                             'INFORMATIVE','EMOTIONAL','URGENT_CTA_DRIVEN',
                             'PREMIUM','TRUST_LED','AUTHORITATIVE_INSTRUCTIONAL'
                         ),

    -- Section 6 & 7: Timelines & Execution
    -- deadline can be NULL — campaigns may be submitted as "ASAP" with no
    -- explicit due date until the marketing head sets one during approval.
    deadline             DATE                       NULL,
    priority             ENUM('HIGH','MEDIUM','LOW') NOT NULL DEFAULT 'MEDIUM',
    budget_tier          ENUM(
                             'NO_BUDGET_ORGANIC','UNDER_50K','FIFTY_K_TO_2L',
                             'TWO_L_TO_10L','ABOVE_10L'
                         ),
    vendor_required      ENUM('YES','NO')           NOT NULL DEFAULT 'NO',
    vendor_type          ENUM('PRINTING','VIDEO_PRODUCTION','INFLUENCER','MEDIA_BUYING'),

    -- Section 8: KPIs
    kpi_type             ENUM(
                             'LEADS','CPL','FOOTFALL','SALES','ENGAGEMENT',
                             'REACH','TICKET_RESOLUTION_COMPLIANCE'
                         ),
    expected_output      ENUM(
                             'UNDER_100_LEADS','HUNDRED_TO_500_LEADS',
                             'FIVE_HUNDRED_TO_1000_LEADS','ABOVE_1000_LEADS'
                         ),

    -- Section 9: Approval
    final_approver       ENUM('DEPARTMENT_HEAD','MARKETING_HEAD','REGIONAL_MANAGER'),

    -- System Metadata
    -- Status workflow: IN_PROGRESS → QC_REVIEW → COMPLETED | REJECTED.
    -- Campaigns auto-route to workers on creation; no approval gates.
    status               ENUM(
                             'IN_PROGRESS','QC_REVIEW','COMPLETED','REJECTED','CANCELLED'
                         )                          NOT NULL DEFAULT 'IN_PROGRESS',
    routing_notes        VARCHAR(500),

    -- Module 2-C: priority/budget inconsistency flag (set when e.g. HIGH
    -- priority + low budget). Recomputed whenever priority/budget changes.
    flagged_inconsistency BOOLEAN                  NOT NULL DEFAULT FALSE,
    inconsistency_reason  VARCHAR(500),

    -- Approval audit trail (Dept + Marketing). Populated by ApprovalService
    -- so each approver's login can show their historical approve/reject lists.
    dept_decision         ENUM('APPROVED','REJECTED'),
    dept_decision_by      INT,
    dept_decision_at      TIMESTAMP NULL,
    marketing_decision    ENUM('APPROVED','REJECTED'),
    marketing_decision_by INT,
    marketing_decision_at TIMESTAMP NULL,
    rejection_reason      VARCHAR(1000),

    created_at           TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP                  DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_camp_requestor FOREIGN KEY (requestor_id)        REFERENCES users(user_id),
    CONSTRAINT fk_camp_dept      FOREIGN KEY (department_id)        REFERENCES departments(department_id),
    CONSTRAINT fk_camp_region    FOREIGN KEY (target_region_id)     REFERENCES regions(region_id),
    CONSTRAINT fk_camp_req_type  FOREIGN KEY (requirement_type_id)  REFERENCES requirement_types(requirement_type_id),
    CONSTRAINT fk_camp_audience  FOREIGN KEY (audience_type_id)     REFERENCES audiences(audience_id)
) ENGINE=InnoDB;

-- Campaign junction tables removed: platform, format, and quantity are now
-- tracked per individual work_task (see work_tasks.platform_id / format_id / quantity).

-- =====================================================================
-- 8.4  Work Tasks (assigned execution units per campaign)
-- =====================================================================
CREATE TABLE IF NOT EXISTS work_tasks (
    task_id                    VARCHAR(20)  NOT NULL PRIMARY KEY,
    campaign_id                INT          NOT NULL,
    assigned_to                INT,
    granular_task_id           VARCHAR(20),
    -- task type is resolved via granular_tasks → task_types (no redundant column here)
    platform_id                VARCHAR(20),
    format_id                  VARCHAR(20),
    quantity                   VARCHAR(50),
    -- Status lifecycle:
    --   ASSIGNED → IN_PROGRESS → QC_REVIEW → COMPLETED
    --                          ↘ REWORK ↗
    --   CANCELLED is set when QC rejects this task or its parent campaign
    --   is rejected/intervention-rejected after partial routing — the work
    --   is closed without producing an approved asset.
    --   HELD is set when a marketing manager temporarily removes the task
    --   from a worker's queue (Module 2-B Capacity Alerts redesign) so the
    --   slot can be redirected toward a higher-priority campaign waiting at
    --   the marketing-head approval gate.
    status                     ENUM(
                                   'ASSIGNED','ACCEPTED','IN_PROGRESS',
                                   'QC_REVIEW','REWORK','COMPLETED','CANCELLED','HELD'
                               )            NOT NULL DEFAULT 'ASSIGNED',
    assigned_at                TIMESTAMP    NULL,
    accepted_at                TIMESTAMP    NULL,
    started_at                 TIMESTAMP    NULL,
    submitted_at               TIMESTAMP    NULL,
    completed_at               TIMESTAMP    NULL,
    total_time_logged_minutes  INT          DEFAULT 0,
    dynamic_deadline           TIMESTAMP    NULL,
    -- Submission payload (worker provides before kicking task into QC_REVIEW).
    submission_notes           VARCHAR(2000),
    asset_url                  VARCHAR(1000),
    created_at                 TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    updated_at                 TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_wt_campaign    FOREIGN KEY (campaign_id)      REFERENCES campaigns(campaign_id)       ON DELETE CASCADE,
    CONSTRAINT fk_wt_user        FOREIGN KEY (assigned_to)      REFERENCES users(user_id),
    CONSTRAINT fk_wt_gran_task   FOREIGN KEY (granular_task_id) REFERENCES granular_tasks(task_id),
    CONSTRAINT fk_wt_task_type   FOREIGN KEY (task_type_id)     REFERENCES task_types(task_type_id),
    CONSTRAINT fk_wt_platform    FOREIGN KEY (platform_id)      REFERENCES platforms(platform_id),
    CONSTRAINT fk_wt_format      FOREIGN KEY (format_id)        REFERENCES creative_formats(format_id)
) ENGINE=InnoDB;

-- =====================================================================
-- 8.5  Approvals & QC Log
-- =====================================================================
CREATE TABLE IF NOT EXISTS approvals_log (
    log_id        INT          NOT NULL AUTO_INCREMENT PRIMARY KEY,
    task_id       INT          NOT NULL,
    reviewer_id   INT          NOT NULL,
    action_taken  ENUM('APPROVED','NEEDS_REWORK','REJECTED') NOT NULL,
    comments      TEXT,
    created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_al_task     FOREIGN KEY (task_id)     REFERENCES work_tasks(task_id) ON DELETE CASCADE,
    CONSTRAINT fk_al_reviewer FOREIGN KEY (reviewer_id) REFERENCES users(user_id)
) ENGINE=InnoDB;

-- =====================================================================
-- 8.6  CRM Feedback & Lead Quality
-- =====================================================================
CREATE TABLE IF NOT EXISTS lead_quality_metrics (
    lead_id                  INT             NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campaign_id              INT             NOT NULL,
    crm_lead_reference_id    VARCHAR(255)    UNIQUE,
    lead_status              ENUM('HOT','WARM','COLD'),
    is_converted             ENUM('YES','NO') NOT NULL DEFAULT 'NO',
    revenue_generated        DECIMAL(10,2)   DEFAULT 0.00,
    generated_at             TIMESTAMP       NULL,
    status                   ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    updated_at               TIMESTAMP       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_lqm_campaign FOREIGN KEY (campaign_id) REFERENCES campaigns(campaign_id)
) ENGINE=InnoDB;
