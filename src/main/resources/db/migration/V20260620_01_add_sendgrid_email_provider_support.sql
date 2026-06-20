ALTER TABLE email_provider_settings
    ADD COLUMN sendgrid_api_key VARCHAR(1000) NULL AFTER aws_region;
