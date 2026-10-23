-- target: tenant
-- GST report ke liye invoice ke waqt customer ki state/GSTIN ka snapshot.
ALTER TABLE invoices
  ADD COLUMN place_of_supply_state_id bigint DEFAULT NULL,
  ADD COLUMN place_of_supply_state varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN customer_gstin varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
