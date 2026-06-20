ALTER TABLE invoices ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE payments ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_invoices_company_deleted_date ON invoices (company_id, is_deleted, invoice_date);
CREATE INDEX idx_payments_company_deleted_date ON payments (company_id, is_deleted, payment_date);
