-- Customers page (+ invoice/payment customer dropdowns size=1000): "ORDER BY created_at DESC" + LIMIT.
-- Bina index ke har page load par poori customers table filesort hoti thi.
CREATE INDEX idx_customers_created_at ON customers (created_at);
