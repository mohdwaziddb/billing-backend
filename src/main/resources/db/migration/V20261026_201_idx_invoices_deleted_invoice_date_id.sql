-- Invoice list / dashboard / reports: "where is_deleted = ? [and invoice_date between ..] order by invoice_date desc, id desc limit N".
-- invoices par invoice_date ka koi index nahi tha: har list page load par poori table scan + filesort.
CREATE INDEX idx_invoices_deleted_invoice_date_id ON invoices (is_deleted, invoice_date, id);
