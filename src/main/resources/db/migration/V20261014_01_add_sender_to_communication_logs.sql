-- target: tenant
-- History sender identity: which provider account each message went from
-- (From-email / SMS Sender-ID / WhatsApp number). Old rows keep NULL -> "--".
ALTER TABLE notification_logs ADD COLUMN sender VARCHAR(255) NULL;
ALTER TABLE email_logs ADD COLUMN provider_name VARCHAR(100) NULL;
ALTER TABLE email_logs ADD COLUMN sender_email VARCHAR(255) NULL;
