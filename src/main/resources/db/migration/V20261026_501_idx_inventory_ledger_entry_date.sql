-- Stock Ledger page: "order by entry_date desc, id desc" + date range filter.
-- Har sale/purchase par row banti hai (sabse tez badhne wali table), bina index ke
-- har page load par poori table filesort hoti thi.
CREATE INDEX idx_inventory_ledger_entry_date_id ON inventory_ledger (entry_date, id);
