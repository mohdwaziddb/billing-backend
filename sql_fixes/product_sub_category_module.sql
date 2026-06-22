CREATE TABLE IF NOT EXISTS product_sub_categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    product_category_id BIGINT NOT NULL,
    sub_category_name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    active BIT(1) NOT NULL DEFAULT b'1',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_product_sub_category_company_category_name UNIQUE (company_id, product_category_id, sub_category_name),
    CONSTRAINT fk_product_sub_category_company FOREIGN KEY (company_id) REFERENCES companies (id),
    CONSTRAINT fk_product_sub_category_category FOREIGN KEY (product_category_id) REFERENCES product_categories (id)
);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS product_sub_category_id BIGINT NULL;

ALTER TABLE products
    ADD CONSTRAINT fk_product_sub_category_product
    FOREIGN KEY (product_sub_category_id) REFERENCES product_sub_categories (id);

CREATE INDEX idx_product_sub_categories_company_category
    ON product_sub_categories (company_id, product_category_id, active);

CREATE INDEX idx_products_product_sub_category
    ON products (product_sub_category_id);
