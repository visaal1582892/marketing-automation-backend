-- =============================================================================
--  V6 – Campaign Specifications Tables
--  New lookup tables: campaign_types, business_verticals, business_types,
--  store_format_types, and their mapping tables.
--  Also adds 4 new columns to the campaigns table.
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------------
-- campaign_types
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS campaign_types (
    campaign_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    campaign_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status              ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- business_verticals
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS business_verticals (
    business_vertical_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    business_vertical_name  VARCHAR(200)              NOT NULL UNIQUE,
    status                  ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- business_types
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS business_types (
    business_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    business_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status              ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- store_format_types
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS store_format_types (
    store_format_type_id    VARCHAR(20)               NOT NULL PRIMARY KEY,
    store_format_type_name  VARCHAR(200)              NOT NULL UNIQUE,
    status                  ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at              TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP                 NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Mapping: business_vertical  →  business_type  (many-to-many)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS business_vertical_business_type_mapping (
    mapping_id           INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_vertical_id VARCHAR(20) NOT NULL,
    business_type_id     VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_bv_bt (business_vertical_id, business_type_id),
    CONSTRAINT fk_bvbt_vertical FOREIGN KEY (business_vertical_id)
        REFERENCES business_verticals(business_vertical_id) ON DELETE CASCADE,
    CONSTRAINT fk_bvbt_type     FOREIGN KEY (business_type_id)
        REFERENCES business_types(business_type_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Mapping: business_type  →  store_format_type  (many-to-many)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS business_type_store_format_mapping (
    mapping_id            INT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    business_type_id      VARCHAR(20) NOT NULL,
    store_format_type_id  VARCHAR(20) NOT NULL,
    UNIQUE KEY uq_bt_sft (business_type_id, store_format_type_id),
    CONSTRAINT fk_btsf_type   FOREIGN KEY (business_type_id)
        REFERENCES business_types(business_type_id) ON DELETE CASCADE,
    CONSTRAINT fk_btsf_format FOREIGN KEY (store_format_type_id)
        REFERENCES store_format_types(store_format_type_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------------
-- Add spec columns to campaigns
-- ---------------------------------------------------------------------------
-- ADD COLUMN IF NOT EXISTS is MariaDB syntax and not supported by MySQL 8.0.
-- Flyway prevents this script from running twice; these columns do not exist after V1 runs.
ALTER TABLE campaigns
    ADD COLUMN store_format_type_id VARCHAR(20) NULL AFTER department_id,
    ADD COLUMN business_type_id VARCHAR(20) NULL AFTER department_id,
    ADD COLUMN business_vertical_id VARCHAR(20) NULL AFTER department_id,
    ADD COLUMN campaign_type_id VARCHAR(20) NULL AFTER department_id;

-- ---------------------------------------------------------------------------
-- Seed data: campaign_types
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO campaign_types (campaign_type_id, campaign_type_name) VALUES
    ('1', 'Branding'),
    ('2', 'Marketing');

-- ---------------------------------------------------------------------------
-- Seed data: business_verticals
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO business_verticals (business_vertical_id, business_vertical_name) VALUES
    ('1', 'Pharma Retail'),
    ('2', 'Diagnostics'),
    ('3', 'Opticals'),
    ('4', 'Insurance'),
    ('5', 'Non-Pharma Retail');

-- ---------------------------------------------------------------------------
-- Seed data: business_types
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO business_types (business_type_id, business_type_name) VALUES
    ('1', 'COCO'),
    ('2', 'COFO'),
    ('3', 'FOFO'),
    ('4', 'Collection Center'),
    ('5', 'Diagnostic Center');

-- ---------------------------------------------------------------------------
-- Seed data: store_format_types
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO store_format_types (store_format_type_id, store_format_type_name) VALUES
    ('1', 'Rural'),
    ('2', 'Urban Regular'),
    ('3', 'Large Format'),
    ('4', 'Home Collection'),
    ('5', 'Walkin'),
    ('6', 'L1'),
    ('7', 'L2'),
    ('8', 'L3');

-- ---------------------------------------------------------------------------
-- Seed mappings: business_vertical → business_type
-- Pharma Retail: COCO, COFO, FOFO
-- Diagnostics: Collection Center, Diagnostic Center
-- Opticals: COCO, COFO, FOFO
-- Insurance: COCO, FOFO
-- Non-Pharma Retail: COCO, COFO, FOFO
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO business_vertical_business_type_mapping (business_vertical_id, business_type_id) VALUES
    ('1', '1'), ('1', '2'), ('1', '3'),
    ('2', '4'), ('2', '5');

-- ---------------------------------------------------------------------------
-- Seed mappings: business_type → store_format_type
-- COCO: Rural, Urban Regular, Large Format
-- COFO: Rural, Urban Regular
-- FOFO: Rural, Urban Regular
-- Collection Center: Home Collection, Walkin
-- Diagnostic Center: Home Collection, Walkin, L1, L2, L3
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO business_type_store_format_mapping (business_type_id, store_format_type_id) VALUES
    ('1', '1'), ('1', '2'), ('1', '3'),
    ('4', '4'), ('4', '5'),
    ('5', '6'), ('5', '7'), ('5', '8');

SET FOREIGN_KEY_CHECKS = 1;
