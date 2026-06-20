ALTER TABLE invoices
    ADD COLUMN refer_by_user_id BIGINT NULL;

CREATE INDEX idx_invoices_refer_by_user_id ON invoices (refer_by_user_id);
CREATE INDEX idx_invoices_company_refer_date ON invoices (company_id, refer_by_user_id, invoice_date);

ALTER TABLE invoices
    ADD CONSTRAINT fk_invoices_refer_by_user
        FOREIGN KEY (refer_by_user_id) REFERENCES users (id);
