-- Outstanding Reminders page (list + summary): "payment_status <> PAID and balance_amount > 0"
-- group by customer_id, min(invoice_date). Zyada tar invoices PAID hote hain, isliye poori invoices
-- table scan ki jagah sirf UNPAID/PARTIAL range padhi jaye.
CREATE INDEX idx_invoices_status_customer_date ON invoices (payment_status, customer_id, invoice_date);
