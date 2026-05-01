-- =====================================================================
--  ONE-SHOT MIGRATION HELPER
--  ---------------------------------------------------------------
--  Run this ONCE if your local database was created with an older
--  schema version. Drops every table in FK-safe order so that
--  schema.sql can recreate them cleanly on next Spring Boot start.
--
--  Usage (MySQL CLI):
--      mysql -u <user> -p <database> < db_drop.sql
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- 8.6 CRM / Lead Quality
DROP TABLE IF EXISTS lead_quality_metrics;

-- 8.5 Approvals
DROP TABLE IF EXISTS approvals_log;

-- 8.4 Work Tasks
DROP TABLE IF EXISTS work_tasks;

-- 8.3 Campaign junction tables
DROP TABLE IF EXISTS campaign_creative_formats;
DROP TABLE IF EXISTS campaign_platforms;
DROP TABLE IF EXISTS campaign_granular_tasks;

-- 8.3 Campaigns
DROP TABLE IF EXISTS campaigns;

-- 8.2 Users
DROP TABLE IF EXISTS users;

-- Designations
DROP TABLE IF EXISTS designations;

-- Routing config
DROP TABLE IF EXISTS role_task_mapping;
DROP TABLE IF EXISTS requirement_role_mapping;

-- Granular tasks
DROP TABLE IF EXISTS granular_tasks;

-- 8.1 Master lookup tables
DROP TABLE IF EXISTS creative_formats;
DROP TABLE IF EXISTS platforms;
DROP TABLE IF EXISTS audiences;
DROP TABLE IF EXISTS regions;
DROP TABLE IF EXISTS task_types;
DROP TABLE IF EXISTS requirement_types;
DROP TABLE IF EXISTS roles;
DROP TABLE IF EXISTS departments;

SET FOREIGN_KEY_CHECKS = 1;
