-- Normalize product category storage from products.category text to products.product_category_id FK.
-- Review on a backup first. This script preserves existing category names by creating
-- active Product Category master rows per company where needed, then backfills products.

ALTER TABLE products
    ADD COLUMN product_category_id BIGINT NULL;

INSERT INTO product_categories (company_id, category_name, description, active, created_at, updated_at)
SELECT DISTINCT p.company_id, TRIM(p.category), NULL, TRUE, NOW(6), NOW(6)
FROM products p
WHERE p.category IS NOT NULL
  AND TRIM(p.category) <> ''
  AND NOT EXISTS (
      SELECT 1
      FROM product_categories pc
      WHERE pc.company_id = p.company_id
        AND LOWER(pc.category_name) = LOWER(TRIM(p.category))
  );

UPDATE products p
JOIN product_categories pc
  ON pc.company_id = p.company_id
 AND LOWER(pc.category_name) = LOWER(TRIM(p.category))
SET p.product_category_id = pc.id
WHERE p.product_category_id IS NULL
  AND p.category IS NOT NULL
  AND TRIM(p.category) <> '';

-- Check before enforcing NOT NULL:
-- SELECT id, name, category FROM products WHERE product_category_id IS NULL;

ALTER TABLE products
    MODIFY product_category_id BIGINT NOT NULL;

CREATE INDEX idx_products_product_category_id ON products(product_category_id);

ALTER TABLE products
    ADD CONSTRAINT fk_products_product_category
        FOREIGN KEY (product_category_id) REFERENCES product_categories(id);

ALTER TABLE products
    DROP COLUMN category;
