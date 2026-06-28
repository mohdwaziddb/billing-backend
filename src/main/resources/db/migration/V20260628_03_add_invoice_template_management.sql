ALTER TABLE companies
    ADD COLUMN bank_name VARCHAR(255) NULL,
    ADD COLUMN bank_account_name VARCHAR(255) NULL,
    ADD COLUMN bank_account_number VARCHAR(255) NULL,
    ADD COLUMN bank_ifsc_code VARCHAR(255) NULL,
    ADD COLUMN bank_branch VARCHAR(255) NULL,
    ADD COLUMN upi_id VARCHAR(255) NULL,
    ADD COLUMN signature_url VARCHAR(500) NULL,
    ADD COLUMN invoice_notes TEXT NULL,
    ADD COLUMN invoice_terms TEXT NULL;

CREATE TABLE IF NOT EXISTS company_invoice_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    default_template_id VARCHAR(120) NOT NULL,
    show_watermark BIT(1) NOT NULL DEFAULT b'0',
    watermark_text VARCHAR(255) NULL,
    show_signature BIT(1) NOT NULL DEFAULT b'1',
    show_qr BIT(1) NOT NULL DEFAULT b'1',
    show_bank_details BIT(1) NOT NULL DEFAULT b'1',
    show_terms BIT(1) NOT NULL DEFAULT b'1',
    show_notes BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    CONSTRAINT uk_company_invoice_settings_company UNIQUE (company_id),
    CONSTRAINT fk_company_invoice_settings_company FOREIGN KEY (company_id) REFERENCES companies(id)
);
