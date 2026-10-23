-- target: tenant
-- 1) Invoice edit: purane invoice_items delete hote waqt released allocations
--    ka FK toot-ta tha. Item link nullable; history invoice_id se rehti hai.
ALTER TABLE invoice_item_allocations MODIFY invoice_item_id bigint NULL;

-- 2) Blank SKU ko NULL nahi kar sakte (column NOT NULL), isliye unique se pehle
--    duplicates check karo. Agar is tenant me duplicate SKU hain to ye statement
--    fail hoga aur migration agle boot par phir try karega: duplicate SKU theek karo:
--    SELECT sku, COUNT(*) FROM products GROUP BY sku HAVING COUNT(*) > 1;
ALTER TABLE products ADD UNIQUE KEY uk_products_sku (sku);
