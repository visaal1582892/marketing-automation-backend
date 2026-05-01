-- =====================================================================
--  Marketing Automation System  ─  Initial Seed Data
--  INSERT IGNORE  ⇒  safe to re-run on every startup.
--
--  Master tables now have ONLY a custom VARCHAR primary key
--  (e.g. role_id = 'ROLE-1'). We seed those values explicitly here.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Roles  (per Section 13 + system role 'Admin')
-- ---------------------------------------------------------------------
INSERT IGNORE INTO roles (role_id, role_name) VALUES
    ('ROLE-1',  'Graphic Designer'),
    ('ROLE-2',  'Photographer'),
    ('ROLE-3',  'Content Writer'),
    ('ROLE-4',  'CRM Specialist'),
    ('ROLE-5',  'Paid Ads Manager'),
    ('ROLE-6',  'SEO Owner'),
    ('ROLE-7',  'Procurement Owner'),
    ('ROLE-8',  'Procurement Manager'),
    ('ROLE-9',  'Offline Operations'),
    ('ROLE-10', 'ORM Owner'),
    ('ROLE-11', 'Admin'),
    ('ROLE-12', 'Requestor'),
    ('ROLE-13', 'Marketing Manager'),
    ('ROLE-14', 'Marketing Creator'),
    ('ROLE-15', 'Head'),
    ('ROLE-16', 'Regional Manager');

-- ---------------------------------------------------------------------
-- Departments  (per Section 1)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO departments (department_id, department_name) VALUES
    ('DEPT-1',  'Sales Operations'),
    ('DEPT-2',  'Franchise'),
    ('DEPT-3',  'Private Label'),
    ('DEPT-4',  'Supply Chain'),
    ('DEPT-5',  'HR'),
    ('DEPT-6',  'Insurance'),
    ('DEPT-7',  'Diagnostics'),
    ('DEPT-8',  'Opticals'),
    ('DEPT-9',  'Security'),
    ('DEPT-10', 'Infra'),
    ('DEPT-11', 'Corporate Sales'),
    ('DEPT-12', 'IT'),
    ('DEPT-13', 'Marketing');

-- ---------------------------------------------------------------------
-- Requirement Types  (per Section 2)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO requirement_types (requirement_type_id, requirement_name) VALUES
    ('REQ-1',  'Social Media Post'),
    ('REQ-2',  'Performance Marketing Campaign'),
    ('REQ-3',  'Store Branding / POSM'),
    ('REQ-4',  'WhatsApp / SMS Campaign'),
    ('REQ-5',  'Landing Page / Website'),
    ('REQ-6',  'SEO Content'),
    ('REQ-7',  'ORM / Reputation Management'),
    ('REQ-8',  'Influencer Marketing'),
    ('REQ-9',  'Video / Testimonial Shoot'),
    ('REQ-10', 'Print Ad / Hoarding'),
    ('REQ-11', 'CMS / Website Updations');

-- ---------------------------------------------------------------------
-- Regions  (per Section 1)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO regions (region_id, region_name) VALUES
    ('REG-1', 'Pan India'),
    ('REG-2', 'South'),
    ('REG-3', 'North'),
    ('REG-4', 'East'),
    ('REG-5', 'West'),
    ('REG-6', 'Specific City');

-- ---------------------------------------------------------------------
-- Audiences  (per Section 3)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO audiences (audience_id, audience_name) VALUES
    ('AUD-1', 'Retail Customers'),
    ('AUD-2', 'Franchise Owners'),
    ('AUD-3', 'Doctors / Clinics'),
    ('AUD-4', 'Job Seekers'),
    ('AUD-5', 'Existing Customers'),
    ('AUD-6', 'B2B Partners'),
    ('AUD-7', 'Internal Employees');

-- ---------------------------------------------------------------------
-- Platforms  (per Section 5)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO platforms (platform_id, platform_name) VALUES
    ('PLAT-1', 'Instagram'),
    ('PLAT-2', 'Facebook'),
    ('PLAT-3', 'Google Ads'),
    ('PLAT-4', 'YouTube'),
    ('PLAT-5', 'WhatsApp'),
    ('PLAT-6', 'In-store'),
    ('PLAT-7', 'Website/CMS'),
    ('PLAT-8', 'Google My Business');

-- ---------------------------------------------------------------------
-- Creative Formats  (per Section 5)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO creative_formats (format_id, format_name) VALUES
    ('FMT-1', 'Static'),
    ('FMT-2', 'Carousel'),
    ('FMT-3', 'Video'),
    ('FMT-4', 'GIF'),
    ('FMT-5', 'PDF'),
    ('FMT-6', 'Backend Data (Text/Links)');

-- ---------------------------------------------------------------------
-- Task Types  (high-level execution buckets)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO task_types (task_type_id, task_name) VALUES
    ('TASK-TYPE-1',  'Design'),
    ('TASK-TYPE-2',  'Photography'),
    ('TASK-TYPE-3',  'Content'),
    ('TASK-TYPE-4',  'CRM'),
    ('TASK-TYPE-5',  'Paid Ads'),
    ('TASK-TYPE-6',  'SEO'),
    ('TASK-TYPE-7',  'Procurement'),
    ('TASK-TYPE-8',  'Operations'),
    ('TASK-TYPE-9',  'ORM'),
    ('TASK-TYPE-10', 'Approval');

-- ---------------------------------------------------------------------
-- Granular Tasks  (Section 13 — public ids 'TASK-1' .. 'TASK-35')
-- task_category: DIGITAL = online / web-based  |  OFFLINE = physical / in-person
-- ---------------------------------------------------------------------
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
    -- Design (TASK-TYPE-1)
    ('TASK-1',  'Flyers, Banners & Standees',                 'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-2',  'GSB Boards & Wall Branding',                 'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-3',  'Glass Stickers & Store POSM',                'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-4',  'UI Designing',                               'TASK-TYPE-1', 'DIGITAL'),
    ('TASK-5',  'Packaging Design',                           'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-6',  'Hoardings & Paper Ads',                      'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-33', 'HR, Corporate & Event Creatives',            'TASK-TYPE-1', 'OFFLINE'),
    ('TASK-34', 'Security, Safety & Infra Signage',           'TASK-TYPE-1', 'OFFLINE'),
    -- Photography (TASK-TYPE-2)
    ('TASK-7',  'Product Photography',                        'TASK-TYPE-2', 'OFFLINE'),
    ('TASK-8',  'Event / Store Inauguration Photoshoot',      'TASK-TYPE-2', 'OFFLINE'),
    ('TASK-9',  'Doctor & MDX Photoshoot',                    'TASK-TYPE-2', 'OFFLINE'),
    -- Content (TASK-TYPE-3)
    ('TASK-10', 'Blog Writing & Publishing',                  'TASK-TYPE-3', 'DIGITAL'),
    ('TASK-11', 'Scripts (Video/Influencer)',                 'TASK-TYPE-3', 'DIGITAL'),
    ('TASK-12', 'Product Descriptions',                       'TASK-TYPE-3', 'DIGITAL'),
    ('TASK-13', 'Website Content & Testimonials',             'TASK-TYPE-3', 'DIGITAL'),
    ('TASK-31', 'CMS Website Product Content Uploads',        'TASK-TYPE-3', 'DIGITAL'),
    -- CRM (TASK-TYPE-4)
    ('TASK-14', 'Push Notifications',                         'TASK-TYPE-4', 'DIGITAL'),
    ('TASK-15', 'SMS & WhatsApp Campaigns',                   'TASK-TYPE-4', 'DIGITAL'),
    ('TASK-16', 'Influencer Collaborations & Campaigns',      'TASK-TYPE-4', 'DIGITAL'),
    ('TASK-32', 'Bulk Data Purchase & Sorting',               'TASK-TYPE-4', 'DIGITAL'),
    -- Paid Ads (TASK-TYPE-5)
    ('TASK-17', 'Google Ads Management',                      'TASK-TYPE-5', 'DIGITAL'),
    ('TASK-18', 'Meta (Facebook/Instagram) Ads',              'TASK-TYPE-5', 'DIGITAL'),
    ('TASK-19', 'YouTube Ads',                                'TASK-TYPE-5', 'DIGITAL'),
    ('TASK-20', 'Conversion Tracking & Analytics',            'TASK-TYPE-5', 'DIGITAL'),
    -- SEO (TASK-TYPE-6)
    ('TASK-21', 'OnPage & OffPage SEO',                       'TASK-TYPE-6', 'DIGITAL'),
    ('TASK-22', 'Technical SEO & Core Web Vitals',            'TASK-TYPE-6', 'DIGITAL'),
    ('TASK-23', 'Keyword Research & Competitor Analysis',     'TASK-TYPE-6', 'DIGITAL'),
    -- Procurement (TASK-TYPE-7)
    ('TASK-24', 'PO Creation & Service Orders',               'TASK-TYPE-7', 'OFFLINE'),
    ('TASK-25', 'Invoice Submission & GRN',                   'TASK-TYPE-7', 'OFFLINE'),
    ('TASK-26', 'Vendor Rate Negotiation',                    'TASK-TYPE-7', 'OFFLINE'),
    -- Operations (TASK-TYPE-8)
    ('TASK-27', 'Store Branding Installation & Launch',       'TASK-TYPE-8', 'OFFLINE'),
    -- ORM (TASK-TYPE-9)
    ('TASK-28', 'Social Media Complaints & Feedback',         'TASK-TYPE-9', 'DIGITAL'),
    ('TASK-29', 'GMB Management & Review Updations',          'TASK-TYPE-9', 'DIGITAL'),
    ('TASK-30', 'Profile Creation (Franchise/Pharmacy)',      'TASK-TYPE-9', 'DIGITAL'),
    ('TASK-35', 'Walk-ins & Operational Reporting',           'TASK-TYPE-9', 'OFFLINE');

-- ---------------------------------------------------------------------
-- Requirement Type  →  Default Role mapping  (Routing Engine config)
-- Determines which role receives tasks for each campaign type.
-- ---------------------------------------------------------------------
INSERT IGNORE INTO requirement_role_mapping (requirement_type_id, default_role_id) VALUES
    ('REQ-1',  'ROLE-1'),   -- Social Media Post          → Graphic Designer
    ('REQ-2',  'ROLE-5'),   -- Performance Marketing       → Paid Ads Manager
    ('REQ-3',  'ROLE-1'),   -- Store Branding / POSM       → Graphic Designer
    ('REQ-4',  'ROLE-4'),   -- WhatsApp / SMS Campaign     → CRM Specialist
    ('REQ-5',  'ROLE-1'),   -- Landing Page / Website      → Graphic Designer (UI)
    ('REQ-6',  'ROLE-6'),   -- SEO Content                 → SEO Owner
    ('REQ-7',  'ROLE-10'),  -- ORM / Reputation Mgmt       → ORM Owner
    ('REQ-8',  'ROLE-4'),   -- Influencer Marketing        → CRM Specialist
    ('REQ-9',  'ROLE-2'),   -- Video / Testimonial Shoot   → Photographer
    ('REQ-10', 'ROLE-1'),   -- Print Ad / Hoarding         → Graphic Designer
    ('REQ-11', 'ROLE-10');  -- CMS / Website Updations     → ORM Owner

-- ---------------------------------------------------------------------
-- Role  ⇄  Granular Task mapping  (Section 13)
-- ---------------------------------------------------------------------
INSERT IGNORE INTO role_task_mapping (role_id, task_id, status) VALUES
    ('ROLE-1', 'TASK-1',  'ACTIVE'), ('ROLE-1', 'TASK-2',  'ACTIVE'), ('ROLE-1', 'TASK-3',  'ACTIVE'), ('ROLE-1', 'TASK-4',  'ACTIVE'),
    ('ROLE-1', 'TASK-5',  'ACTIVE'), ('ROLE-1', 'TASK-6',  'ACTIVE'), ('ROLE-1', 'TASK-33', 'ACTIVE'), ('ROLE-1', 'TASK-34', 'ACTIVE'),
    ('ROLE-2', 'TASK-7',  'ACTIVE'), ('ROLE-2', 'TASK-8',  'ACTIVE'), ('ROLE-2', 'TASK-9',  'ACTIVE'),
    ('ROLE-3', 'TASK-10', 'ACTIVE'), ('ROLE-3', 'TASK-11', 'ACTIVE'), ('ROLE-3', 'TASK-12', 'ACTIVE'), ('ROLE-3', 'TASK-13', 'ACTIVE'), ('ROLE-3', 'TASK-31', 'ACTIVE'),
    ('ROLE-4', 'TASK-14', 'ACTIVE'), ('ROLE-4', 'TASK-15', 'ACTIVE'), ('ROLE-4', 'TASK-16', 'ACTIVE'), ('ROLE-4', 'TASK-32', 'ACTIVE'),
    ('ROLE-5', 'TASK-17', 'ACTIVE'), ('ROLE-5', 'TASK-18', 'ACTIVE'), ('ROLE-5', 'TASK-19', 'ACTIVE'), ('ROLE-5', 'TASK-20', 'ACTIVE'),
    ('ROLE-6', 'TASK-21', 'ACTIVE'), ('ROLE-6', 'TASK-22', 'ACTIVE'), ('ROLE-6', 'TASK-23', 'ACTIVE'),
    ('ROLE-7', 'TASK-24', 'ACTIVE'), ('ROLE-7', 'TASK-25', 'ACTIVE'),
    ('ROLE-8', 'TASK-26', 'ACTIVE'),
    ('ROLE-9', 'TASK-27', 'ACTIVE'),
    ('ROLE-10', 'TASK-16', 'ACTIVE'), ('ROLE-10', 'TASK-28', 'ACTIVE'), ('ROLE-10', 'TASK-29', 'ACTIVE'),
    ('ROLE-10', 'TASK-30', 'ACTIVE'), ('ROLE-10', 'TASK-31', 'ACTIVE'), ('ROLE-10', 'TASK-35', 'ACTIVE');
