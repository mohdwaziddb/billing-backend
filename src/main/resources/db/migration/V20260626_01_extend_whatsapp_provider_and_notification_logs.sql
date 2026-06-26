ALTER TABLE whatsapp_provider_settings
    ADD COLUMN provider_config LONGTEXT NULL AFTER api_url;

ALTER TABLE notification_logs
    ADD COLUMN provider_name VARCHAR(120) NULL AFTER message,
    ADD COLUMN message_id VARCHAR(255) NULL AFTER provider_name,
    ADD COLUMN failure_reason TEXT NULL AFTER message_id;
