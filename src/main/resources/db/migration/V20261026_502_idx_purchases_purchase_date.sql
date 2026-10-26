-- Purchases page: "order by purchase_date desc, id desc" + date range filter.
CREATE INDEX idx_purchases_purchase_date_id ON purchases (purchase_date, id);
