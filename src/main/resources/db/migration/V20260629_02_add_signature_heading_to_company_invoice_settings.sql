ALTER TABLE company_invoice_settings
    ADD COLUMN signature_heading VARCHAR(255) NULL AFTER signature_label;
