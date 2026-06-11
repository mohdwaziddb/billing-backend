CREATE TABLE IF NOT EXISTS notification_channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    channel_name VARCHAR(50) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_notification_channels_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS email_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    template_name VARCHAR(150) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_body TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_email_templates_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS email_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    template_id BIGINT,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    email_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    sent_by VARCHAR(255),
    sent_at DATETIME,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_email_logs_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT fk_email_logs_template FOREIGN KEY (template_id) REFERENCES email_templates(id)
);

CREATE INDEX idx_notification_channels_company ON notification_channels(company_id);
CREATE INDEX idx_email_templates_company ON email_templates(company_id);
CREATE INDEX idx_email_templates_active ON email_templates(is_active);
CREATE INDEX idx_email_logs_company ON email_logs(company_id);
CREATE INDEX idx_email_logs_template ON email_logs(template_id);
CREATE INDEX idx_email_logs_status ON email_logs(status);
CREATE INDEX idx_email_logs_sent_at ON email_logs(sent_at);

CREATE TABLE IF NOT EXISTS sms_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    template_name VARCHAR(150) NOT NULL,
    template_body TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_sms_templates_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS email_provider_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    aws_access_key VARCHAR(255),
    aws_secret_key VARCHAR(255),
    aws_region VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_email_provider_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS sms_provider_settings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    provider_name VARCHAR(255) NOT NULL,
    api_url TEXT NOT NULL,
    username VARCHAR(255),
    password VARCHAR(255),
    sender_id VARCHAR(100),
    channel_name VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_sms_provider_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    channel VARCHAR(30) NOT NULL,
    template_id BIGINT,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    message TEXT,
    provider_response TEXT,
    status VARCHAR(20) NOT NULL,
    sent_by VARCHAR(255),
    sent_at DATETIME,
    created_at DATETIME NOT NULL,
    created_by VARCHAR(255),
    updated_at DATETIME NOT NULL,
    updated_by VARCHAR(255),
    CONSTRAINT fk_notification_logs_company FOREIGN KEY (company_id) REFERENCES companies(id)
);

CREATE INDEX idx_sms_templates_company ON sms_templates(company_id);
CREATE INDEX idx_sms_templates_active ON sms_templates(is_active);
CREATE INDEX idx_email_provider_company ON email_provider_settings(company_id);
CREATE INDEX idx_sms_provider_company ON sms_provider_settings(company_id);
CREATE INDEX idx_notification_logs_company ON notification_logs(company_id);
CREATE INDEX idx_notification_logs_channel ON notification_logs(channel);
CREATE INDEX idx_notification_logs_status ON notification_logs(status);
CREATE INDEX idx_notification_logs_sent_at ON notification_logs(sent_at);
