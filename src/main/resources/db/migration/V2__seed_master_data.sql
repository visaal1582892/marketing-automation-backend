-- =============================================================================
--  V2 – Seed All Master / Reference Data
--  IDs are simple sequential numbers stored as VARCHAR ("1", "2", …).
--  Users are NOT seeded here; they live in DataInitializer (passwords).
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- DEPARTMENTS
-- =============================================================================
INSERT IGNORE INTO departments (department_id, department_name, status) VALUES
  (1, 'Marketing', 'ACTIVE'),
(10, 'Supply Chain', 'ACTIVE'),
(11, 'Infra', 'ACTIVE'),
(12, 'Opticals', 'ACTIVE'),
(13, 'Insurance', 'ACTIVE'),
(2, 'Sales Operations', 'ACTIVE'),
(3, 'Franchise', 'ACTIVE'),
(4, 'Operations', 'ACTIVE'),
(5, 'HR', 'ACTIVE'),
(6, 'Finance', 'ACTIVE'),
(7, 'IT', 'ACTIVE'),
(8, 'Pharmacy', 'ACTIVE'),
(9, 'Diagnostics', 'ACTIVE');

-- =============================================================================
-- DESIGNATIONS
-- =============================================================================
INSERT IGNORE INTO designations (designation_id, designation_name) VALUES
  ('1',  'Dy. Manager'),
  ('2',  'Graphic Designer'),
  ('3',  'Sr. Executive'),
  ('4',  'Assistant Manager'),
  ('5',  'Sr. Graphic Designer'),
  ('6',  'Executive'),
  ('7',  'Sr. Content Writer'),
  ('8',  'SEO'),
  ('9',  'Intern');

-- =============================================================================
-- ROLES
-- =============================================================================
INSERT IGNORE INTO roles (role_id, role_name) VALUES
  ('1',  'Admin'),
  ('2',  'Graphic Designer'),
  ('3',  'Photographer'),
  ('4',  'Content Writer'),
  ('5',  'CRM Specialist'),
  ('6',  'Paid Ads Manager'),
  ('7',  'SEO Owner'),
  ('8',  'Procurement Owner'),
  ('9',  'Procurement Manager'),
  ('10', 'Offline Operations'),
  ('11', 'ORM Owner'),
  ('12', 'Requestor'),
  ('13', 'Marketing Manager');

-- =============================================================================
-- REQUIREMENT TYPES
-- =============================================================================
INSERT IGNORE INTO requirement_types (requirement_type_id, requirement_name) VALUES
  ('1',  'Social Media Post'),
  ('2',  'Performance Marketing Campaign'),
  ('3',  'Store Branding / POSM'),
  ('4',  'Digital Video / Reel'),
  ('5',  'Event / Store Activation'),
  ('6',  'SEO Content'),
  ('7',  'Email / WhatsApp Campaign'),
  ('8',  'Photography'),
  ('9',  'CRM / Lead Nurture Campaign'),
  ('10', 'Print Ad / Hoarding'),
  ('11', 'ORM / Review Management'),
  ('12', 'Product Launch Campaign');

-- =============================================================================
-- TASK TYPES
-- =============================================================================
INSERT IGNORE INTO task_types (task_type_id, task_name) VALUES
  ('1',  'Graphic Design'),
  ('2',  'Photography'),
  ('3',  'Videography'),
  ('4',  'Content Writing'),
  ('5',  'SEO'),
  ('6',  'Paid Ads / Performance Marketing'),
  ('7',  'CRM Management'),
  ('8',  'ORM / Reputation Management'),
  ('9',  'Procurement'),
  ('10', 'Offline Operations');

-- =============================================================================
-- REGIONS
-- =============================================================================
INSERT IGNORE INTO regions (region_id, region_name) VALUES
  ('1',  'Hyderabad'),
  ('2',  'Secunderabad'),
  ('3',  'Bengaluru'),
  ('4',  'Mysuru'),
  ('5',  'Vijayawada'),
  ('6',  'Visakhapatnam'),
  ('7',  'Chennai'),
  ('8',  'Pune'),
  ('9',  'Mumbai'),
  ('10', 'Delhi NCR'),
  ('11', 'Kolkata'),
  ('12', 'Ahmedabad'),
  ('13', 'PAN India / All Stores');

-- =============================================================================
-- AUDIENCES
-- =============================================================================
INSERT IGNORE INTO audiences (audience_id, audience_name) VALUES
  ('1', 'Retail Customers'),
  ('2', 'Franchise Owners'),
  ('3', 'Healthcare Professionals'),
  ('4', 'Senior Citizens'),
  ('5', 'Parents / Families'),
  ('6', 'Corporate Employees'),
  ('7', 'Internal Employees'),
  ('8', 'Job Seekers');

-- =============================================================================
-- OFFER TYPES
-- =============================================================================
INSERT IGNORE INTO offer_types (offer_type_id, offer_type_name) VALUES
  ('1', 'Discount %'),
  ('2', 'Flat Discount'),
  ('3', 'Free Checkup'),
  ('4', 'Bundle Offer'),
  ('5', 'Franchise ROI Pitch'),
  ('6', 'Salary / Hiring Offer');

-- =============================================================================
-- VENDOR TYPES
-- =============================================================================
INSERT IGNORE INTO vendor_types (vendor_type_id, vendor_type_name) VALUES
  ('1', 'Printing'),
  ('2', 'Video Production'),
  ('3', 'Influencer'),
  ('4', 'Media Buying'),
  ('5', 'Event Management'),
  ('6', 'Photography Studio'),
  ('7', 'Digital Agency');

-- =============================================================================
-- BUSINESS OBJECTIVES
-- =============================================================================
INSERT IGNORE INTO business_objectives (business_objective_id, business_objective_name) VALUES
  ('1', 'Lead Generation'),
  ('2', 'Walk-ins / Store Footfall'),
  ('3', 'Brand Awareness'),
  ('4', 'Product Launch'),
  ('5', 'Offer Promotion'),
  ('6', 'Recruitment'),
  ('7', 'Internal Communication'),
  ('8', 'Operational / Compliance / Safety');

-- =============================================================================
-- LANGUAGES
-- =============================================================================
INSERT IGNORE INTO languages (language_id, language_name) VALUES
  ('1', 'English'),
  ('2', 'Hindi'),
  ('3', 'Telugu'),
  ('4', 'Tamil'),
  ('5', 'Kannada'),
  ('6', 'Multi-language');

-- =============================================================================
-- TONES
-- =============================================================================
INSERT IGNORE INTO tones (tone_id, tone_name) VALUES
  ('1', 'Informative'),
  ('2', 'Emotional'),
  ('3', 'Urgent / CTA Driven'),
  ('4', 'Premium'),
  ('5', 'Trust-led'),
  ('6', 'Authoritative / Instructional');

-- =============================================================================
-- SUPPORTING PROOFS
-- =============================================================================
INSERT IGNORE INTO supporting_proofs (supporting_proof_id, supporting_proof_name) VALUES
  ('1', 'Store Count'),
  ('2', 'Customer Base'),
  ('3', 'Years in Market'),
  ('4', 'Doctor Recommendation'),
  ('5', 'Testimonials Available');

-- =============================================================================
-- BUDGET TIERS
-- =============================================================================
INSERT IGNORE INTO budget_tiers (budget_tier_id, budget_tier_name) VALUES
  ('1', 'No Budget (Organic)'),
  ('2', '< Rs.50K'),
  ('3', 'Rs.50K to Rs.2L'),
  ('4', 'Rs.2L to Rs.10L'),
  ('5', 'Rs.10L+');

-- =============================================================================
-- KPI TYPES
-- =============================================================================
INSERT IGNORE INTO kpi_types (kpi_type_id, kpi_type_name) VALUES
  ('1', 'Leads'),
  ('2', 'CPL'),
  ('3', 'Footfall'),
  ('4', 'Sales'),
  ('5', 'Engagement'),
  ('6', 'Reach'),
  ('7', 'Ticket Resolution / Compliance');

-- =============================================================================
-- EXPECTED OUTPUTS
-- =============================================================================
INSERT IGNORE INTO expected_outputs (expected_output_id, expected_output_name) VALUES
  ('1', 'Under 100 Leads'),
  ('2', '100 to 500 Leads'),
  ('3', '500 to 1000 Leads'),
  ('4', '1000+ Leads');

-- =============================================================================
-- GRANULAR TASKS  (TASK-1 … TASK-35)
-- task_category: DIGITAL (4,10-23,28-32)  |  OFFLINE (1-3,5-9,24-27,33-35)
-- =============================================================================

-- Graphic Design (task_type_id='1')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-1',  'Flyer / Brochure Design',                  '1', 'OFFLINE'),
  ('TASK-2',  'Banner / Standee Design',                  '1', 'OFFLINE'),
  ('TASK-3',  'Poster Design',                            '1', 'OFFLINE'),
  ('TASK-4',  'Digital Banner / Display Ad Creative',     '1', 'DIGITAL'),
  ('TASK-5',  'In-Store POSM Design',                     '1', 'OFFLINE'),
  ('TASK-6',  'Outdoor Hoarding / Billboard Design',      '1', 'OFFLINE'),
  ('TASK-7',  'Event Backdrop / Stage Design',            '1', 'OFFLINE'),
  ('TASK-8',  'Branded Uniform / Vehicle Wrap Design',    '1', 'OFFLINE'),
  ('TASK-9',  'WhatsApp Creative / Sticker Design',       '1', 'OFFLINE'),
  ('TASK-10', 'Social Media Post Graphic',                '1', 'DIGITAL'),
  ('TASK-11', 'Email Template Design',                    '1', 'DIGITAL'),
  ('TASK-12', 'YouTube Thumbnail / Channel Art',          '1', 'DIGITAL'),
  ('TASK-13', 'Story / Reel Frame Design',                '1', 'DIGITAL');

-- Photography (task_type_id='2')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-14', 'Product / Store Photography & Editing',    '2', 'DIGITAL');

-- Videography (task_type_id='3')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-15', 'Short Reel / Vertical Video Production',   '3', 'DIGITAL'),
  ('TASK-16', 'Long-form Video Shoot & Edit',             '3', 'DIGITAL');

-- Content Writing (task_type_id='4')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-17', 'Social Media Caption Writing',             '4', 'DIGITAL'),
  ('TASK-18', 'Blog / Article Writing',                   '4', 'DIGITAL'),
  ('TASK-19', 'Email / Newsletter Copy',                  '4', 'DIGITAL'),
  ('TASK-20', 'WhatsApp / SMS Campaign Copy',             '4', 'DIGITAL');

-- SEO (task_type_id='5')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-21', 'On-page SEO Optimisation',                 '5', 'DIGITAL'),
  ('TASK-22', 'Keyword Research & Mapping',               '5', 'DIGITAL'),
  ('TASK-23', 'Google Business Profile Update',           '5', 'DIGITAL');

-- Procurement – Offline (task_type_id='9')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-24', 'Print Vendor Coordination',                '9', 'OFFLINE'),
  ('TASK-25', 'Outdoor Media / Hoarding Booking',         '9', 'OFFLINE'),
  ('TASK-26', 'Promotional Material Procurement',         '9', 'OFFLINE'),
  ('TASK-27', 'Event Material & Logistics Procurement',   '9', 'OFFLINE');

-- Paid Ads / Performance Marketing (task_type_id='6')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-28', 'Google Ads Campaign Setup',                '6', 'DIGITAL'),
  ('TASK-29', 'Meta (Facebook / Instagram) Ads Setup',   '6', 'DIGITAL'),
  ('TASK-30', 'Google Ads Optimisation',                  '6', 'DIGITAL'),
  ('TASK-31', 'Programmatic / Display Ad Management',    '6', 'DIGITAL'),
  ('TASK-32', 'Performance Report & Insights',            '6', 'DIGITAL');

-- Offline Operations (task_type_id='10')
INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category) VALUES
  ('TASK-33', 'Store / Event Activation Setup',           '10', 'OFFLINE'),
  ('TASK-34', 'Leaflet / Door-hanger Distribution',       '10', 'OFFLINE'),
  ('TASK-35', 'In-Store Display Arrangement',             '10', 'OFFLINE');

-- =============================================================================
-- REQUIREMENT → ROLE MAPPING  (routing engine uses this to pick the lead team)
-- =============================================================================
INSERT IGNORE INTO requirement_role_mapping (requirement_type_id, default_role_id) VALUES
  ('1',  '2'),   -- Social Media Post          → Graphic Designer
  ('2',  '6'),   -- Performance Marketing       → Paid Ads Manager
  ('3',  '2'),   -- Store Branding / POSM       → Graphic Designer
  ('4',  '3'),   -- Digital Video / Reel        → Photographer (Videography)
  ('5',  '10'),  -- Event / Store Activation    → Offline Operations
  ('6',  '7'),   -- SEO Content                 → SEO Owner
  ('7',  '4'),   -- Email / WhatsApp Campaign   → Content Writer
  ('8',  '3'),   -- Photography                 → Photographer
  ('9',  '5'),   -- CRM / Lead Nurture          → CRM Specialist
  ('10', '8'),   -- Print Ad / Hoarding         → Procurement Owner
  ('11', '11'),  -- ORM / Review Management     → ORM Owner
  ('12', '6');   -- Product Launch Campaign     → Paid Ads Manager

-- =============================================================================
-- ROLE → TASK MAPPING  (routing engine assigns these tasks for each role)
-- =============================================================================
INSERT IGNORE INTO role_task_mapping (role_id, task_id) VALUES
  -- Graphic Designer
  ('2', 'TASK-1'), ('2', 'TASK-2'), ('2', 'TASK-3'), ('2', 'TASK-4'),
  ('2', 'TASK-5'), ('2', 'TASK-6'), ('2', 'TASK-7'), ('2', 'TASK-8'),
  ('2', 'TASK-9'), ('2', 'TASK-10'), ('2', 'TASK-11'), ('2', 'TASK-12'), ('2', 'TASK-13'),
  -- Photographer / Videographer
  ('3', 'TASK-14'), ('3', 'TASK-15'), ('3', 'TASK-16'),
  -- Content Writer
  ('4', 'TASK-17'), ('4', 'TASK-18'), ('4', 'TASK-19'), ('4', 'TASK-20'),
  -- SEO Owner
  ('7', 'TASK-21'), ('7', 'TASK-22'), ('7', 'TASK-23'),
  -- Procurement Owner
  ('8', 'TASK-24'), ('8', 'TASK-25'), ('8', 'TASK-26'), ('8', 'TASK-27'),
  -- Paid Ads Manager
  ('6', 'TASK-28'), ('6', 'TASK-29'), ('6', 'TASK-30'), ('6', 'TASK-31'), ('6', 'TASK-32'),
  -- Offline Operations
  ('10', 'TASK-33'), ('10', 'TASK-34'), ('10', 'TASK-35');

-- =============================================================================
-- DYNAMIC QUESTIONS  (QUES-1 … QUES-N)
-- Task-specific questionnaire prompts answered by the assigned team member.
-- =============================================================================

-- ── Graphic Design (common to most design tasks) ────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-1',  'What size / dimensions are required for this creative?',
              'TEXT', NULL, 1),
  ('QUES-2',  'Is a print-ready (bleed + crop marks) file needed?',
              'DROPDOWN', '["Yes","No"]', 1),
  ('QUES-3',  'Which colour profile should be used?',
              'DROPDOWN', '["CMYK (Print)","RGB (Digital)","CMYK (Print) & RGB (Digital)"]', 1),
  ('QUES-4',  'What file formats should be delivered?',
              'MULTISELECT', '["PDF","PNG","JPG","SVG","AI","PSD","EPS","CDN"]', 1),
  ('QUES-5',  'Are brand guidelines / style guide files attached?',
              'DROPDOWN', '["Yes, in the drive link","No, use standard brand kit"]', 0),
  ('QUES-6',  'How many design variants / versions are needed?',
              'NUMBER', NULL, 1),
  ('QUES-7',  'Is there reference artwork or a competitor sample to follow?',
              'TEXT', NULL, 0),
  ('QUES-8',  'What is the primary call-to-action (CTA) text?',
              'TEXT', NULL, 1),
  ('QUES-9',  'List the headline text and supporting body copy.',
              'TEXTAREA', NULL, 1),
  ('QUES-10', 'Are there any mandatory disclaimers or legal text to include?',
              'TEXTAREA', NULL, 0);

-- ── Flyer / Brochure Design (TASK-1) ────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-11', 'Is this a single-page flyer or a multi-page brochure?',
              'DROPDOWN', '["Single-page Flyer","Bi-fold Brochure","Tri-fold Brochure","Multi-page Booklet"]', 1),
  ('QUES-12', 'Paper stock / GSM preference (e.g. 130 GSM matte)?',
              'TEXT', NULL, 0),
  ('QUES-13', 'Quantity required for print?',
              'NUMBER', NULL, 0);

-- ── Banner / Standee Design (TASK-2) ────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-14', 'What type of banner / standee is this?',
              'DROPDOWN', '["Pull-up / Roll-up Standee","X-Banner","Hoarding Banner","Fabric Backdrop","Vinyl Banner"]', 1),
  ('QUES-15', 'Physical dimensions (width × height in feet or mm)?',
              'TEXT', NULL, 1);

-- ── Outdoor Hoarding Design (TASK-6) ────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-16', 'Hoarding location / site address?',
              'TEXT', NULL, 1),
  ('QUES-17', 'Billboard dimensions (width × height)?',
              'TEXT', NULL, 1),
  ('QUES-18', 'Is the vendor supplying the installation or is coordination needed?',
              'DROPDOWN', '["Vendor handles installation","Our team coordinates"]', 1);

-- ── Digital Banner / Display Ad Creative (TASK-4) ───────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-19', 'Which ad platform(s) are these creatives for?',
              'MULTISELECT', '["Google Display Network","Meta (Facebook/Instagram)","LinkedIn","YouTube","Programmatic DSP","Other"]', 1),
  ('QUES-20', 'What ad sizes are needed?',
              'MULTISELECT', '["300×250 (Medium Rectangle)","728×90 (Leaderboard)","160×600 (Wide Skyscraper)","320×50 (Mobile Banner)","1200×628 (Social Link Preview)","1080×1080 (Square)","1080×1920 (Story/Reel)"]', 1),
  ('QUES-21', 'Should the file be animated (HTML5 / GIF) or static?',
              'DROPDOWN', '["Static","Animated GIF","HTML5"]', 1);

-- ── Social Media Post Graphic (TASK-10) ─────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-22', 'Which social platforms is this post for?',
              'MULTISELECT', '["Instagram","Facebook","LinkedIn","Twitter / X","YouTube (Community)","WhatsApp"]', 1),
  ('QUES-23', 'What type of social post is required?',
              'DROPDOWN', '["Single Image Post","Carousel","Story / Reel Frame","Cover Photo","Profile Picture"]', 1),
  ('QUES-24', 'Is scheduled posting required or only asset delivery?',
              'DROPDOWN', '["Asset delivery only","Post scheduling included"]', 1);

-- ── Email Template Design (TASK-11) ─────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-25', 'What email platform will this be sent on?',
              'DROPDOWN', '["Mailchimp","HubSpot","Zoho Campaigns","WhatsApp Business","Other"]', 1),
  ('QUES-26', 'Is an HTML-coded template needed or only a visual mockup?',
              'DROPDOWN', '["Visual mockup (image)","HTML-coded template"]', 1),
  ('QUES-27', 'What is the email subject line?',
              'TEXT', NULL, 1);

-- ── Photography (TASK-14) ────────────────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-28', 'Type of photography required?',
              'DROPDOWN', '["Product Photography","Store / Outlet Photography","Event / Launch Photography","Team / Corporate Portrait","Lifestyle / Model Shoot"]', 1),
  ('QUES-29', 'Shoot location(s)?',
              'TEXT', NULL, 1),
  ('QUES-30', 'Preferred shoot date and time window?',
              'TEXT', NULL, 1),
  ('QUES-31', 'How many final edited images are needed?',
              'NUMBER', NULL, 1),
  ('QUES-32', 'Any specific lighting, angle, or background preference?',
              'TEXTAREA', NULL, 0),
  ('QUES-33', 'Image usage rights (social, print, digital ads, all)?',
              'DROPDOWN', '["Social Media Only","Digital Ads","Print","All / Unlimited"]', 1);

-- ── Short Reel / Vertical Video Production (TASK-15) ────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-34', 'Target video length (e.g. 15s, 30s, 60s)?',
              'DROPDOWN', '["15 seconds","30 seconds","60 seconds","90 seconds","Custom"]', 1),
  ('QUES-35', 'Shoot location / is stock footage acceptable?',
              'TEXT', NULL, 1),
  ('QUES-36', 'Is a voiceover or background music required?',
              'DROPDOWN', '["Voiceover only","Background music only","Both","None"]', 0),
  ('QUES-37', 'Output format required?',
              'MULTISELECT', '["MP4 (H.264)","MOV","Instagram Reel (9:16)","YouTube Short (9:16)","Landscape (16:9)"]', 1),
  ('QUES-38', 'Script / storyboard available or needs to be created?',
              'DROPDOWN', '["Script provided","Storyboard provided","Needs to be created by team"]', 1);

-- ── Long-form Video Shoot & Edit (TASK-16) ───────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-39', 'Type of long-form video?',
              'DROPDOWN', '["Corporate Brand Film","Product Demo / Explainer","Event Highlight Reel","Testimonial / Case Study","Training / Internal Video"]', 1),
  ('QUES-40', 'Estimated final video duration?',
              'TEXT', NULL, 1),
  ('QUES-41', 'Number of shoot days / sessions required?',
              'NUMBER', NULL, 1);

-- ── Social Media Caption Writing (TASK-17) ───────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-42', 'Which platforms are these captions for?',
              'MULTISELECT', '["Instagram","Facebook","LinkedIn","Twitter / X","YouTube","WhatsApp"]', 1),
  ('QUES-43', 'How many posts need captions?',
              'NUMBER', NULL, 1),
  ('QUES-44', 'Character / word limit per caption (if any)?',
              'TEXT', NULL, 0),
  ('QUES-45', 'Should hashtags be included? How many?',
              'TEXT', NULL, 0),
  ('QUES-46', 'Is an emoji set allowed?',
              'DROPDOWN', '["Yes","No","Limited (2-3 max)"]', 0);

-- ── Blog / Article Writing (TASK-18) ────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-47', 'Target keyword(s) for SEO?',
              'TEXTAREA', NULL, 1),
  ('QUES-48', 'Desired article word count?',
              'DROPDOWN', '["500-800 words","800-1200 words","1200-2000 words","2000+ words"]', 1),
  ('QUES-49', 'Topic / title of the article?',
              'TEXT', NULL, 1),
  ('QUES-50', 'Target audience for this content?',
              'TEXT', NULL, 1),
  ('QUES-51', 'Should this include an FAQ section?',
              'DROPDOWN', '["Yes","No"]', 0),
  ('QUES-52', 'Are internal links to be included? Which pages?',
              'TEXTAREA', NULL, 0);

-- ── Email / Newsletter Copy (TASK-19) ────────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-53', 'Email subject line (or provide 2-3 options for A/B test)?',
              'TEXT', NULL, 1),
  ('QUES-54', 'Target audience segment for this email?',
              'TEXT', NULL, 1),
  ('QUES-55', 'What is the primary offer or message in the email?',
              'TEXTAREA', NULL, 1),
  ('QUES-56', 'Any existing email template to follow?',
              'DROPDOWN', '["Yes, use existing template","No, create fresh"]', 0);

-- ── WhatsApp / SMS Campaign Copy (TASK-20) ───────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-57', 'Type of messaging channel?',
              'DROPDOWN', '["WhatsApp Bulk Message","WhatsApp Template (Meta-approved)","SMS","Both WhatsApp & SMS"]', 1),
  ('QUES-58', 'Approximate recipient count?',
              'NUMBER', NULL, 0),
  ('QUES-59', 'Message goal (promo code, appointment, footfall)?',
              'TEXT', NULL, 1),
  ('QUES-60', 'Max character limit or word count for the message?',
              'TEXT', NULL, 0);

-- ── On-page SEO Optimisation (TASK-21) ───────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-61', 'Which pages need on-page SEO?',
              'TEXTAREA', NULL, 1),
  ('QUES-62', 'Target keyword(s) per page?',
              'TEXTAREA', NULL, 1),
  ('QUES-63', 'Current SEO score or Lighthouse score if available?',
              'TEXT', NULL, 0),
  ('QUES-64', 'Should meta titles and descriptions be rewritten?',
              'DROPDOWN', '["Yes","No","Only missing ones"]', 1),
  ('QUES-65', 'Is image alt-text optimisation needed?',
              'DROPDOWN', '["Yes","No"]', 0);

-- ── Keyword Research & Mapping (TASK-22) ────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-66', 'Product / service category for keyword research?',
              'TEXT', NULL, 1),
  ('QUES-67', 'Target geographic region for search?',
              'TEXT', NULL, 0),
  ('QUES-68', 'Preferred keyword research tool?',
              'DROPDOWN', '["Google Keyword Planner","SEMrush","Ahrefs","Ubersuggest","No preference"]', 0),
  ('QUES-69', 'Should competitor keyword gaps be included?',
              'DROPDOWN', '["Yes","No"]', 0);

-- ── Google Business Profile Update (TASK-23) ─────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-70', 'Which GBP locations need updating?',
              'TEXTAREA', NULL, 1),
  ('QUES-71', 'What needs to be updated?',
              'MULTISELECT', '["Store Hours","Photos","Posts / Offers","Description","Services","Q&A Responses","Review Replies"]', 1),
  ('QUES-72', 'Are updated photo assets being provided?',
              'DROPDOWN', '["Yes","No, use current store photos"]', 0);

-- ── Print Vendor Coordination (TASK-24) ─────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-73', 'What print material needs to be procured?',
              'MULTISELECT', '["Flyers","Banners","Standees","Brochures","Danglers","Stickers","Carry Bags","T-Shirts / Uniforms","Other"]', 1),
  ('QUES-74', 'Quantity required per item?',
              'TEXTAREA', NULL, 1),
  ('QUES-75', 'Delivery location(s)?',
              'TEXT', NULL, 1),
  ('QUES-76', 'Required delivery date?',
              'DATE', NULL, 1),
  ('QUES-77', 'Approved vendor to use (or open to quotes)?',
              'DROPDOWN', '["Use approved vendor","Obtain 3 quotes and recommend","Urgent — nearest vendor"]', 1),
  ('QUES-78', 'Budget approved for this print job?',
              'TEXT', NULL, 0);

-- ── Outdoor Media / Hoarding Booking (TASK-25) ──────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-79', 'Desired location / area for the hoarding?',
              'TEXT', NULL, 1),
  ('QUES-80', 'Duration of the hoarding contract (weeks/months)?',
              'TEXT', NULL, 1),
  ('QUES-81', 'Maximum budget for hoarding booking?',
              'TEXT', NULL, 1),
  ('QUES-82', 'Any preferred media owner or vendor?',
              'TEXT', NULL, 0);

-- ── Event Material & Logistics Procurement (TASK-27) ─────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-83', 'Event date and venue?',
              'TEXT', NULL, 1),
  ('QUES-84', 'List of materials / items needed for the event?',
              'TEXTAREA', NULL, 1),
  ('QUES-85', 'Estimated budget for event procurement?',
              'TEXT', NULL, 0);

-- ── Google Ads Campaign Setup (TASK-28) ─────────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-86', 'What is the Google Ads campaign goal?',
              'DROPDOWN', '["Lead Generation (Conversions)","Website Traffic","Brand Awareness (Reach)","Local Store Visits","App Downloads"]', 1),
  ('QUES-87', 'Target keywords or ad themes?',
              'TEXTAREA', NULL, 1),
  ('QUES-88', 'Daily / monthly budget for this campaign?',
              'TEXT', NULL, 1),
  ('QUES-89', 'Target locations for the campaign?',
              'TEXT', NULL, 1),
  ('QUES-90', 'Landing page URL for the ads?',
              'TEXT', NULL, 1),
  ('QUES-91', 'Are conversion tracking and Google Analytics linked?',
              'DROPDOWN', '["Yes, already set up","No, needs setup","Not sure"]', 1);

-- ── Meta (Facebook / Instagram) Ads Setup (TASK-29) ─────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-92', 'What is the Meta campaign objective?',
              'DROPDOWN', '["Lead Generation","Traffic","Reach / Brand Awareness","Conversions","Video Views","Engagement"]', 1),
  ('QUES-93', 'Which Meta placements are needed?',
              'MULTISELECT', '["Facebook Feed","Instagram Feed","Stories","Reels","Messenger","Audience Network"]', 1),
  ('QUES-94', 'Target audience definition (age, interests, behaviour)?',
              'TEXTAREA', NULL, 1),
  ('QUES-95', 'Daily / total campaign budget?',
              'TEXT', NULL, 1),
  ('QUES-96', 'Is a Meta Pixel or Conversions API already installed?',
              'DROPDOWN', '["Yes","No","Not sure"]', 1),
  ('QUES-97', 'Campaign start and end dates?',
              'TEXT', NULL, 1);

-- ── Performance Report & Insights (TASK-32) ──────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-98', 'Which platforms should the report cover?',
              'MULTISELECT', '["Google Ads","Meta Ads","SEO","Email","WhatsApp","Organic Social","All"]', 1),
  ('QUES-99', 'Reporting period (week, month, quarter)?',
              'TEXT', NULL, 1),
  ('QUES-100','Format of the report?',
              'DROPDOWN', '["PDF Summary","Google Sheets Dashboard","PowerPoint Deck","In-app / CRM"]', 1);

-- ── Store / Event Activation Setup (TASK-33) ────────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-101', 'Store / venue address for the activation?',
               'TEXT', NULL, 1),
  ('QUES-102', 'Activation date and time?',
               'TEXT', NULL, 1),
  ('QUES-103', 'List of activities planned (e.g. free checkup, product demo)?',
               'TEXTAREA', NULL, 1),
  ('QUES-104', 'Is an external team / vendor needed?',
               'DROPDOWN', '["Yes","No"]', 0);

-- ── Leaflet / Door-hanger Distribution (TASK-34) ────────────────────────────
INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-105', 'Target distribution area / pin codes?',
               'TEXT', NULL, 1),
  ('QUES-106', 'Number of leaflets to distribute?',
               'NUMBER', NULL, 1),
  ('QUES-107', 'Preferred distribution date?',
               'DATE', NULL, 1);

-- =============================================================================
-- TASK → QUESTION MAPPING
-- =============================================================================

-- TASK-1: Flyer / Brochure Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-1','QUES-1'),('TASK-1','QUES-2'),('TASK-1','QUES-3'),('TASK-1','QUES-4'),
  ('TASK-1','QUES-5'),('TASK-1','QUES-6'),('TASK-1','QUES-7'),('TASK-1','QUES-8'),
  ('TASK-1','QUES-9'),('TASK-1','QUES-10'),('TASK-1','QUES-11'),('TASK-1','QUES-12'),
  ('TASK-1','QUES-13');

-- TASK-2: Banner / Standee Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-2','QUES-1'),('TASK-2','QUES-3'),('TASK-2','QUES-4'),('TASK-2','QUES-5'),
  ('TASK-2','QUES-6'),('TASK-2','QUES-8'),('TASK-2','QUES-9'),('TASK-2','QUES-10'),
  ('TASK-2','QUES-14'),('TASK-2','QUES-15');

-- TASK-3: Poster Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-3','QUES-1'),('TASK-3','QUES-2'),('TASK-3','QUES-3'),('TASK-3','QUES-4'),
  ('TASK-3','QUES-5'),('TASK-3','QUES-6'),('TASK-3','QUES-8'),('TASK-3','QUES-9'),
  ('TASK-3','QUES-10');

-- TASK-4: Digital Banner / Display Ad Creative
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-4','QUES-4'),('TASK-4','QUES-5'),('TASK-4','QUES-6'),('TASK-4','QUES-8'),
  ('TASK-4','QUES-9'),('TASK-4','QUES-10'),('TASK-4','QUES-19'),('TASK-4','QUES-20'),
  ('TASK-4','QUES-21');

-- TASK-5: In-Store POSM Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-5','QUES-1'),('TASK-5','QUES-2'),('TASK-5','QUES-3'),('TASK-5','QUES-4'),
  ('TASK-5','QUES-5'),('TASK-5','QUES-6'),('TASK-5','QUES-8'),('TASK-5','QUES-9'),
  ('TASK-5','QUES-10'),('TASK-5','QUES-13');

-- TASK-6: Outdoor Hoarding Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-6','QUES-3'),('TASK-6','QUES-4'),('TASK-6','QUES-5'),('TASK-6','QUES-8'),
  ('TASK-6','QUES-9'),('TASK-6','QUES-10'),('TASK-6','QUES-16'),('TASK-6','QUES-17'),
  ('TASK-6','QUES-18');

-- TASK-7: Event Backdrop Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-7','QUES-1'),('TASK-7','QUES-3'),('TASK-7','QUES-4'),('TASK-7','QUES-5'),
  ('TASK-7','QUES-8'),('TASK-7','QUES-9'),('TASK-7','QUES-15');

-- TASK-8: Branded Uniform / Vehicle Wrap Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-8','QUES-1'),('TASK-8','QUES-3'),('TASK-8','QUES-4'),('TASK-8','QUES-5'),
  ('TASK-8','QUES-6'),('TASK-8','QUES-7');

-- TASK-9: WhatsApp Creative / Sticker Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-9','QUES-4'),('TASK-9','QUES-6'),('TASK-9','QUES-8'),('TASK-9','QUES-9');

-- TASK-10: Social Media Post Graphic
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-10','QUES-4'),('TASK-10','QUES-5'),('TASK-10','QUES-6'),('TASK-10','QUES-8'),
  ('TASK-10','QUES-9'),('TASK-10','QUES-10'),('TASK-10','QUES-22'),('TASK-10','QUES-23'),
  ('TASK-10','QUES-24');

-- TASK-11: Email Template Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-11','QUES-4'),('TASK-11','QUES-5'),('TASK-11','QUES-8'),('TASK-11','QUES-9'),
  ('TASK-11','QUES-25'),('TASK-11','QUES-26'),('TASK-11','QUES-27');

-- TASK-12: YouTube Thumbnail / Channel Art
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-12','QUES-4'),('TASK-12','QUES-5'),('TASK-12','QUES-6'),('TASK-12','QUES-9');

-- TASK-13: Story / Reel Frame Design
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-13','QUES-4'),('TASK-13','QUES-5'),('TASK-13','QUES-6'),('TASK-13','QUES-8'),
  ('TASK-13','QUES-9'),('TASK-13','QUES-22');

-- TASK-14: Photography & Editing
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-14','QUES-28'),('TASK-14','QUES-29'),('TASK-14','QUES-30'),('TASK-14','QUES-31'),
  ('TASK-14','QUES-32'),('TASK-14','QUES-33'),('TASK-14','QUES-4');

-- TASK-15: Short Reel / Vertical Video Production
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-15','QUES-34'),('TASK-15','QUES-35'),('TASK-15','QUES-36'),('TASK-15','QUES-37'),
  ('TASK-15','QUES-38'),('TASK-15','QUES-8');

-- TASK-16: Long-form Video Shoot & Edit
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-16','QUES-39'),('TASK-16','QUES-40'),('TASK-16','QUES-41'),('TASK-16','QUES-36'),
  ('TASK-16','QUES-37'),('TASK-16','QUES-38');

-- TASK-17: Social Media Caption Writing
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-17','QUES-42'),('TASK-17','QUES-43'),('TASK-17','QUES-44'),('TASK-17','QUES-45'),
  ('TASK-17','QUES-46'),('TASK-17','QUES-8');

-- TASK-18: Blog / Article Writing
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-18','QUES-47'),('TASK-18','QUES-48'),('TASK-18','QUES-49'),('TASK-18','QUES-50'),
  ('TASK-18','QUES-51'),('TASK-18','QUES-52');

-- TASK-19: Email / Newsletter Copy
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-19','QUES-53'),('TASK-19','QUES-54'),('TASK-19','QUES-55'),('TASK-19','QUES-56');

-- TASK-20: WhatsApp / SMS Copy
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-20','QUES-57'),('TASK-20','QUES-58'),('TASK-20','QUES-59'),('TASK-20','QUES-60');

-- TASK-21: On-page SEO Optimisation
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-21','QUES-61'),('TASK-21','QUES-62'),('TASK-21','QUES-63'),('TASK-21','QUES-64'),
  ('TASK-21','QUES-65');

-- TASK-22: Keyword Research & Mapping
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-22','QUES-66'),('TASK-22','QUES-67'),('TASK-22','QUES-68'),('TASK-22','QUES-69');

-- TASK-23: Google Business Profile Update
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-23','QUES-70'),('TASK-23','QUES-71'),('TASK-23','QUES-72');

-- TASK-24: Print Vendor Coordination
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-24','QUES-73'),('TASK-24','QUES-74'),('TASK-24','QUES-75'),('TASK-24','QUES-76'),
  ('TASK-24','QUES-77'),('TASK-24','QUES-78');

-- TASK-25: Outdoor Media / Hoarding Booking
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-25','QUES-79'),('TASK-25','QUES-80'),('TASK-25','QUES-81'),('TASK-25','QUES-82');

-- TASK-26: Promotional Material Procurement
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-26','QUES-73'),('TASK-26','QUES-74'),('TASK-26','QUES-75'),('TASK-26','QUES-76'),
  ('TASK-26','QUES-78');

-- TASK-27: Event Material & Logistics Procurement
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-27','QUES-83'),('TASK-27','QUES-84'),('TASK-27','QUES-85'),('TASK-27','QUES-75');

-- TASK-28: Google Ads Campaign Setup
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-28','QUES-86'),('TASK-28','QUES-87'),('TASK-28','QUES-88'),('TASK-28','QUES-89'),
  ('TASK-28','QUES-90'),('TASK-28','QUES-91');

-- TASK-29: Meta Ads Setup
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-29','QUES-92'),('TASK-29','QUES-93'),('TASK-29','QUES-94'),('TASK-29','QUES-95'),
  ('TASK-29','QUES-96'),('TASK-29','QUES-97');

-- TASK-30: Google Ads Optimisation
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-30','QUES-86'),('TASK-30','QUES-88'),('TASK-30','QUES-89'),('TASK-30','QUES-91');

-- TASK-31: Programmatic / Display Ad Management
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-31','QUES-19'),('TASK-31','QUES-20'),('TASK-31','QUES-88'),('TASK-31','QUES-89');

-- TASK-32: Performance Report & Insights
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-32','QUES-98'),('TASK-32','QUES-99'),('TASK-32','QUES-100');

-- TASK-33: Store / Event Activation Setup
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-33','QUES-101'),('TASK-33','QUES-102'),('TASK-33','QUES-103'),('TASK-33','QUES-104');

-- TASK-34: Leaflet / Door-hanger Distribution
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-34','QUES-105'),('TASK-34','QUES-106'),('TASK-34','QUES-107');

-- TASK-35: In-Store Display Arrangement
INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-35','QUES-101'),('TASK-35','QUES-102');


-- user rows inserting
-- role_id is stored in user_roles junction table, NOT directly on users
INSERT IGNORE INTO users (
user_id, full_name, email, password_hash,
department_id, designation_id,
skill_level, current_active_tasks, status, created_at
) VALUES
(1,'System Administrator','admin@medplus.com','$2a$10$LyooB.pM8GNRM8D0elI10.plNV63G.pjXklINfbh.VHvGoLzmuaMS',1,1,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(2,'Sampath Kumar Thummanapelli','sampathkumart@medplusindia.com','$2a$10$VbSfgf5MqiXVR5hhvht3e.mIg2nQ5RP7pNjuGkuRcJfGIEoqoJ1W2',1,5,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(3,'Govardhan Kurva','govardhank@medplusindia.com','$2a$10$UarxD15T7AnfSbL71chhReOzjSd7eW3xQP640KFyf33ckrDjU5G06',1,5,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(4,'Raju Kandakatla','rajukandakatla@medplusindia.com','$2a$10$e5Txc/eG3iNUdk4CsqNkBu/6s4MYAuqI3Bn0c.0xNUx4OkeDQ4Yu2',1,5,'JUNIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(5,'Venkatesh Talari','venkatesht@medplusindia.com','$2a$10$QyllBsUKIg3u7F32iVVe2erM6HpDaVUhKFS/FoZzf5JBmuddrh0LC',1,2,'JUNIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(6,'Bodhan Kumar Neelam','bodhankumar.n@medplusindia.com','$2a$10$3L2Nj9DhDSXWUl6IoKqrP.f329mGrwwc0A9zascL1AlydoKEIGeFO',1,3,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(7,'Aneela Suneetha Sakilay','suneetha.s@medplusindia.com','$2a$10$abRDpiPlHIaOgjBUBn6kHOBbbAiD4hlqIHFx3p4mET8WGRYd/sPMK',1,7,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(8,'Suresh Yerrabelli','sureshy@medplusindia.com','$2a$10$IzdvzYd7R0bZ7TqIlwswAODbIVOaPF3wB9vDPQKkt14JvYRTQx7pm',1,6,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(9,'Charan reddy L','charanreddy.l@medplusindia.com','$2a$10$bEhfmNwrZ0NPrKiK8EEfAODp0TBGW.6k07nVtNK9Y5A/V7/Qt7Fym',1,8,'JUNIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(10,'Ganesh Rathod','ganeshr@medplusindia.com','$2a$10$/auWmbVz0tjMH/5ivJkPReFbP.m9GMeSE5kYlXNM0x.MfkPOx.yS6',1,3,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(11,'Bhadraiah Paidipelly','bhadraiahp@medplusindia.com','$2a$10$vyQ2na8ZRWEz5E9BI42BmeYhkqfQCdj5YxWmdQYKh6QeAMBu7omfq',1,1,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(12,'Suresh Dravidam','suresh.d@medplusindia.com','$2a$10$BmoG3Cv.zUvhBFyS35TaB.0fHSpH8Vw8H9iCihL/a0VZDi3H5eitG',1,6,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(13,'Veer Raju Pydimalla','veerarajup@medplusindia.com','$2a$10$8hQUZJkgYbqdNcjzaSWTe.QHaLn47mK2f8oM6j8NPPCvMygLHw8uG',1,4,'JUNIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(14,'Prabhu Kumar Paradesi','prabhukumar.p@medplusindia.com','$2a$10$hXF4yqvniKED2mfGf.K.sey8hajzwSaUzjnO.cqKWDon.nB6kKanS',1,6,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(15,'Purushottam Balamallu Ijjagiri','purushottambalamallu@medplusindia.com','$2a$10$Pdp8abP.aK7fardGtdsva.2i7oSsP6DkH/wkPA46ISYFfq9KBk5uu',1,4,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(16,'Mahesh Kumar Bijja','maheshkumarb@medplusindia.com','$2a$10$kGV9glX336DoEG2Jm.D0Ie5KDVKAnIZy5rofr41yYvQ4DVSwsNwgq',1,6,'SENIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(17,'Rakesh Dudapaka','rakesh.d@medplusindia.com','$2a$10$U.foczTk0aYQVzqppHa6ue1PtU1T5J6oQTr9TbrJ3HzkXCBuhSuve',2,6,'JUNIOR',0,'ACTIVE','2026-04-29 07:12:00'),
(54,'Rohit','rohit@medplus.com','$2a$10$7ar70.hmqpICz0hBODxGsuLoSi7MRwEBQOHW0.6bhewTdMoAr6L3W',7,1,'SENIOR',0,'ACTIVE','2026-04-29 12:15:00'),
(55,'Yogesh G','yogeshg@medplusindia.com','$2a$10$seOHtFEKHu8baR5ZBowDhuoU2LY8zUoczGAOr2X.Bsbc/WsxvVcUe',8,1,'SENIOR',0,'ACTIVE','2026-04-29 12:26:00'),
(56,'Amarnath P','amarnathp@medplusindia.com','$2a$10$iUOjdW53Gtbu.dxzVt9OO.KgIPEo6i5t1AYGl.xWfD6FgQp//6LYu',3,1,'SENIOR',0,'ACTIVE','2026-04-29 12:27:00'),
(57,'Hari D','harid@medplusindia.com','$2a$10$hZQZljDIAKHZvGIIh4/EH.VyzgyD.xGo83x4B9wJTM6VLuty5SjHW',5,1,'SENIOR',0,'ACTIVE','2026-04-29 12:28:00'),
(58,'Shashikanth P','shashikanth.p@medplusindia.com','$2a$10$xpqFpY68mjjB6TNOw2CG2uhe.1642mb.oI9h1LseymUBm92NuApKe',9,1,'SENIOR',0,'ACTIVE','2026-04-29 12:29:00'),
(59,'Kanda Samy','kandasamy@medplusindia.com','$2a$10$ZJ0LH9DdFbRXYZ42Ny80e.P/m6/jCNfnpSeHTwZNVFbtEKmwHfpT2',10,1,'SENIOR',0,'ACTIVE','2026-04-29 12:30:00'),
(60,'Prathibha R','prathibha.r@medplusindia.com','$2a$10$E26DVtXNMktoFORbZ7CHG.yFKKmCjy2n6Y0u/Iq9x/tW5ypehNeKC',9,1,'SENIOR',0,'ACTIVE','2026-04-29 12:31:00'),
(61,'Anvesh Varma P','anveshvarma.p@medplusindia.com','$2a$10$Gt/JXEH5V0.ZtaBTcRuSxOIZi2gGpEJggpdcmEOqubSK0BjJGDcmW',11,1,'SENIOR',0,'ACTIVE','2026-04-29 12:32:00'),
(62,'Devaraj','devaraj@medplusindia.com','$2a$10$GThEA0jmRsvvvnN7ltGnEuArRL3DHP4yGp3dRJd344LGYS0lQLRP.',12,1,'SENIOR',0,'ACTIVE','2026-04-29 12:33:00'),
(63,'Suman Rao V','sumanraov@medplusib.com','$2a$10$WWV6mhNoy/jIiZjX8ghsvukx.mo6WOb30N/N91IJwlxvW1KFgmjfy',13,1,'SENIOR',0,'ACTIVE','2026-04-29 12:35:00'),
(64,'requestor','requestor@medplus.com','$2a$10$kvNIiJ7tDZ/SCN/2EuVodeGN8EHvT7fXVKI8kNG0DO/UC45tgFXPG',7,1,'SENIOR',0,'ACTIVE','2026-04-29 13:08:00'),
(65,'Thulasiram K','thulasiram.k@medplusindia.com','$2a$10$v9LkwfG.fK.NlRnTYqmEBuWtQVnNcZthPhwL0BPL0in7sDfx89LJK',11,1,'SENIOR',0,'ACTIVE','2026-04-29 14:38:00'),
(66,'Nirmalya D','nirmalya.d@medplusindia.com','$2a$10$7MecVZcmejTR4dT1AwyuB.rBFXcPC2yYM3Zs6LggdIROqe29SMBOG',1,1,'JUNIOR',0,'ACTIVE','2026-04-29 16:15:00'),
(67,'Debarati R','debarati.r@medplusindia.com','$2a$10$dhF7N0FT4VbIZNZcEJ4x/e9iA8xkIS96N6tqBaVIb4.PAJYZNksNW',8,1,'SENIOR',0,'ACTIVE','2026-04-29 16:15:00');

-- Assign each user's role(s) via the user_roles junction table
INSERT IGNORE INTO user_roles (user_id, role_id) VALUES
(1,'1'),(2,'2'),(3,'2'),(4,'2'),(5,'2'),
(6,'4'),(7,'5'),(8,'6'),(9,'7'),(10,'8'),
(11,'1'),(12,'10'),(13,'10'),(14,'11'),(15,'13'),
(16,'8'),(17,'3'),
(54,'1'),
(55,'12'),(56,'12'),(57,'12'),(58,'12'),(59,'12'),
(60,'12'),(61,'12'),(62,'12'),(63,'12'),(64,'12'),
(65,'12'),(66,'13'),(67,'12');

-- =============================================================================
-- "OTHER" TASK — allows requestors to describe a custom task not in the list
-- =============================================================================

INSERT IGNORE INTO task_types (task_type_id, task_name, status) VALUES
  ('11', 'Other', 'ACTIVE');

INSERT IGNORE INTO granular_tasks (task_id, task_name, task_type_id, task_category, status) VALUES
  ('TASK-OTHER', 'Other (Specify Details Below)', '11', NULL, 'ACTIVE');

INSERT IGNORE INTO dynamic_questions (question_id, question_text, field_type, options, is_required) VALUES
  ('QUES-OTHER', 'Please describe the task requirements clearly and in detail', 'TEXTAREA', NULL, 1);

INSERT IGNORE INTO task_question_mapping (granular_task_id, question_id) VALUES
  ('TASK-OTHER', 'QUES-OTHER');

-- Route "Other" tasks to the Marketing Manager (role 13) who triages custom work
INSERT IGNORE INTO role_task_mapping (role_id, task_id) VALUES
  ('13', 'TASK-OTHER');

SET FOREIGN_KEY_CHECKS = 1;
