ALTER TABLE sms_provider_settings
    ADD COLUMN provider_config LONGTEXT NULL AFTER template_id;
