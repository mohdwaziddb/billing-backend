-- Identify legacy email provider rows that can fail primitive boolean hydration.
SELECT id, company_id, provider_name, sender_email, smtp_tls_enabled, is_active
FROM email_provider_settings
WHERE smtp_tls_enabled IS NULL
   OR is_active IS NULL;

-- Backfill NULL values before tightening the schema.
UPDATE email_provider_settings
SET smtp_tls_enabled = 1
WHERE smtp_tls_enabled IS NULL;

UPDATE email_provider_settings
SET is_active = 1
WHERE is_active IS NULL;

-- Keep future rows non-null at the database level.
ALTER TABLE email_provider_settings
    MODIFY smtp_tls_enabled TINYINT(1) NOT NULL DEFAULT 1,
    MODIFY is_active TINYINT(1) NOT NULL DEFAULT 1;
