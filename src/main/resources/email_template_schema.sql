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
