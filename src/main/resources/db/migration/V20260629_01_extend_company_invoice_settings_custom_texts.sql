ALTER TABLE company_invoice_settings
    ADD COLUMN signature_label VARCHAR(255) NULL AFTER show_signature,
    ADD COLUMN note_text TEXT NULL AFTER show_notes,
    ADD COLUMN terms_text TEXT NULL AFTER note_text,
    ADD COLUMN footer_credit VARCHAR(255) NULL AFTER terms_text;
