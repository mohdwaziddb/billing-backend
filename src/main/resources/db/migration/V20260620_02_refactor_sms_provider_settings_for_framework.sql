ALTER TABLE sms_provider_settings
    ADD COLUMN provider_type VARCHAR(50) NULL AFTER provider_name,
    ADD COLUMN auth_key VARCHAR(1000) NULL AFTER api_url,
    ADD COLUMN template_id VARCHAR(255) NULL AFTER sender_id;

UPDATE sms_provider_settings
SET provider_type = 'MSG91'
WHERE provider_type IS NULL;

ALTER TABLE sms_provider_settings
    MODIFY provider_type VARCHAR(50) NOT NULL;
