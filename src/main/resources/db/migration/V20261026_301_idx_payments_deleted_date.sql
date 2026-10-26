-- Payments list / KPI summary / payment hierarchy / dashboard: "is_deleted = false" + payment_date
-- range + "ORDER BY payment_date DESC, id DESC" LIMIT. Bina index ke har page load par poori payments
-- table scan + filesort hoti thi. (InnoDB secondary index me id khud aakhir me hota hai.)
CREATE INDEX idx_payments_deleted_date ON payments (is_deleted, payment_date);
