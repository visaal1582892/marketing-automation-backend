-- Add store_id and contact_number to campaigns table
ALTER TABLE campaigns
    ADD COLUMN store_id        VARCHAR(100) NULL AFTER store_format_type_id,
    ADD COLUMN contact_number  VARCHAR(20)  NULL AFTER store_id;
