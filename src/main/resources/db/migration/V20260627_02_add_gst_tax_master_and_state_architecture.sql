CREATE TABLE IF NOT EXISTS state_master (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    state_code VARCHAR(16) NOT NULL,
    state_name VARCHAR(120) NOT NULL,
    country_name VARCHAR(120) NOT NULL DEFAULT 'India',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    CONSTRAINT uk_state_master_code UNIQUE (state_code),
    CONSTRAINT uk_state_master_name_country UNIQUE (state_name, country_name)
);

CREATE TABLE IF NOT EXISTS tax_master (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    tax_name VARCHAR(120) NOT NULL,
    tax_code VARCHAR(40) NOT NULL,
    tax_type VARCHAR(40) NOT NULL,
    rate DECIMAL(5,2) NOT NULL,
    description VARCHAR(255) NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NULL,
    updated_by VARCHAR(255) NULL,
    CONSTRAINT fk_tax_master_company FOREIGN KEY (company_id) REFERENCES companies(id),
    CONSTRAINT uk_tax_master_company_name UNIQUE (company_id, tax_name),
    CONSTRAINT uk_tax_master_company_code UNIQUE (company_id, tax_code)
);

ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS state_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS gstin VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS gst_registered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS composition_scheme BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE companies
    ADD CONSTRAINT fk_companies_state
    FOREIGN KEY (state_id) REFERENCES state_master(id);

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS city VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS pincode VARCHAR(20) NULL,
    ADD COLUMN IF NOT EXISTS state_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS country VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS gstin VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS gst_registered BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE customers
    ADD CONSTRAINT fk_customers_state
    FOREIGN KEY (state_id) REFERENCES state_master(id);

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS tax_master_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS taxable BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE products
    ADD CONSTRAINT fk_products_tax_master
    FOREIGN KEY (tax_master_id) REFERENCES tax_master(id);

ALTER TABLE invoices
    ADD COLUMN IF NOT EXISTS taxable_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS cgst_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS sgst_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS igst_total DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS round_off DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS grand_total DECIMAL(12,2) NOT NULL DEFAULT 0.00;

ALTER TABLE invoice_items
    ADD COLUMN IF NOT EXISTS tax_master_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS tax_name VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS tax_rate DECIMAL(5,2) NULL,
    ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS taxable_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS cgst_rate DECIMAL(5,2) NULL,
    ADD COLUMN IF NOT EXISTS cgst_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS sgst_rate DECIMAL(5,2) NULL,
    ADD COLUMN IF NOT EXISTS sgst_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS igst_rate DECIMAL(5,2) NULL,
    ADD COLUMN IF NOT EXISTS igst_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS discount_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS net_amount DECIMAL(12,2) NULL,
    ADD COLUMN IF NOT EXISTS grand_amount DECIMAL(12,2) NULL;

ALTER TABLE invoice_items
    ADD CONSTRAINT fk_invoice_items_tax_master
    FOREIGN KEY (tax_master_id) REFERENCES tax_master(id);

CREATE INDEX IF NOT EXISTS idx_tax_master_company_active_deleted ON tax_master(company_id, active, deleted);
CREATE INDEX IF NOT EXISTS idx_products_company_tax_master ON products(company_id, tax_master_id);
CREATE INDEX IF NOT EXISTS idx_customers_company_state ON customers(company_id, state_id);
CREATE INDEX IF NOT EXISTS idx_companies_state ON companies(state_id);

INSERT INTO state_master (state_code, state_name, country_name, is_active)
SELECT * FROM (
    SELECT 'AN', 'Andaman and Nicobar Islands', 'India', TRUE UNION ALL
    SELECT 'AP', 'Andhra Pradesh', 'India', TRUE UNION ALL
    SELECT 'AR', 'Arunachal Pradesh', 'India', TRUE UNION ALL
    SELECT 'AS', 'Assam', 'India', TRUE UNION ALL
    SELECT 'BR', 'Bihar', 'India', TRUE UNION ALL
    SELECT 'CH', 'Chandigarh', 'India', TRUE UNION ALL
    SELECT 'CT', 'Chhattisgarh', 'India', TRUE UNION ALL
    SELECT 'DN', 'Dadra and Nagar Haveli and Daman and Diu', 'India', TRUE UNION ALL
    SELECT 'DL', 'Delhi', 'India', TRUE UNION ALL
    SELECT 'GA', 'Goa', 'India', TRUE UNION ALL
    SELECT 'GJ', 'Gujarat', 'India', TRUE UNION ALL
    SELECT 'HR', 'Haryana', 'India', TRUE UNION ALL
    SELECT 'HP', 'Himachal Pradesh', 'India', TRUE UNION ALL
    SELECT 'JK', 'Jammu and Kashmir', 'India', TRUE UNION ALL
    SELECT 'JH', 'Jharkhand', 'India', TRUE UNION ALL
    SELECT 'KA', 'Karnataka', 'India', TRUE UNION ALL
    SELECT 'KL', 'Kerala', 'India', TRUE UNION ALL
    SELECT 'LA', 'Ladakh', 'India', TRUE UNION ALL
    SELECT 'LD', 'Lakshadweep', 'India', TRUE UNION ALL
    SELECT 'MP', 'Madhya Pradesh', 'India', TRUE UNION ALL
    SELECT 'MH', 'Maharashtra', 'India', TRUE UNION ALL
    SELECT 'MN', 'Manipur', 'India', TRUE UNION ALL
    SELECT 'ML', 'Meghalaya', 'India', TRUE UNION ALL
    SELECT 'MZ', 'Mizoram', 'India', TRUE UNION ALL
    SELECT 'NL', 'Nagaland', 'India', TRUE UNION ALL
    SELECT 'OD', 'Odisha', 'India', TRUE UNION ALL
    SELECT 'PY', 'Puducherry', 'India', TRUE UNION ALL
    SELECT 'PB', 'Punjab', 'India', TRUE UNION ALL
    SELECT 'RJ', 'Rajasthan', 'India', TRUE UNION ALL
    SELECT 'SK', 'Sikkim', 'India', TRUE UNION ALL
    SELECT 'TN', 'Tamil Nadu', 'India', TRUE UNION ALL
    SELECT 'TS', 'Telangana', 'India', TRUE UNION ALL
    SELECT 'TR', 'Tripura', 'India', TRUE UNION ALL
    SELECT 'UP', 'Uttar Pradesh', 'India', TRUE UNION ALL
    SELECT 'UK', 'Uttarakhand', 'India', TRUE UNION ALL
    SELECT 'WB', 'West Bengal', 'India', TRUE
) seeded
WHERE NOT EXISTS (
    SELECT 1 FROM state_master existing WHERE existing.state_code = seeded.state_code
);

UPDATE companies
SET gstin = COALESCE(gstin, tax_id),
    gst_registered = CASE
        WHEN COALESCE(gstin, tax_id) IS NOT NULL AND TRIM(COALESCE(gstin, tax_id)) <> '' THEN TRUE
        ELSE gst_registered
    END
WHERE gstin IS NULL OR gstin = '';

UPDATE customers
SET gstin = COALESCE(gstin, gst_no),
    gst_registered = CASE
        WHEN COALESCE(gstin, gst_no) IS NOT NULL AND TRIM(COALESCE(gstin, gst_no)) <> '' THEN TRUE
        ELSE gst_registered
    END
WHERE gstin IS NULL OR gstin = '';

UPDATE companies c
JOIN state_master s ON LOWER(TRIM(c.state)) = LOWER(TRIM(s.state_name))
SET c.state_id = s.id
WHERE c.state_id IS NULL
  AND c.state IS NOT NULL
  AND TRIM(c.state) <> '';

UPDATE customers c
JOIN state_master s ON LOWER(TRIM(c.state)) = LOWER(TRIM(s.state_name))
SET c.state_id = s.id
WHERE c.state_id IS NULL
  AND c.state IS NOT NULL
  AND TRIM(c.state) <> '';

INSERT INTO tax_master (company_id, tax_name, tax_code, tax_type, rate, description, is_default, active, deleted)
SELECT c.id,
       CONCAT('GST ', TRIM(TRAILING '.00' FROM FORMAT(seed.rate, 2)), '%'),
       CONCAT('GST_', REPLACE(TRIM(TRAILING '.00' FROM FORMAT(seed.rate, 2)), '.', '_')),
       'GST',
       seed.rate,
       CONCAT('Default GST ', TRIM(TRAILING '.00' FROM FORMAT(seed.rate, 2)), '%'),
       CASE WHEN seed.rate = 18.00 THEN TRUE ELSE FALSE END,
       TRUE,
       FALSE
FROM companies c
JOIN (
    SELECT 0.00 AS rate UNION ALL
    SELECT 5.00 UNION ALL
    SELECT 12.00 UNION ALL
    SELECT 18.00 UNION ALL
    SELECT 28.00
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM tax_master tm
    WHERE tm.company_id = c.id
      AND tm.tax_type = 'GST'
      AND tm.rate = seed.rate
      AND tm.deleted = FALSE
);

INSERT INTO tax_master (company_id, tax_name, tax_code, tax_type, rate, description, is_default, active, deleted)
SELECT p.company_id,
       CONCAT('GST ', TRIM(TRAILING '.00' FROM FORMAT(p.tax_percent, 2)), '%'),
       CONCAT('GST_', REPLACE(TRIM(TRAILING '.00' FROM FORMAT(p.tax_percent, 2)), '.', '_')),
       'GST',
       p.tax_percent,
       'Migrated from legacy product tax percent',
       FALSE,
       TRUE,
       FALSE
FROM (
    SELECT DISTINCT company_id, tax_percent
    FROM products
    WHERE tax_percent IS NOT NULL
) p
WHERE NOT EXISTS (
    SELECT 1
    FROM tax_master tm
    WHERE tm.company_id = p.company_id
      AND tm.tax_type = 'GST'
      AND tm.rate = p.tax_percent
      AND tm.deleted = FALSE
);

UPDATE products p
JOIN tax_master tm
  ON tm.company_id = p.company_id
 AND tm.tax_type = 'GST'
 AND tm.rate = COALESCE(p.tax_percent, 0.00)
 AND tm.deleted = FALSE
SET p.tax_master_id = tm.id,
    p.taxable = CASE WHEN COALESCE(p.tax_percent, 0.00) > 0 THEN TRUE ELSE p.taxable END
WHERE p.tax_master_id IS NULL;

UPDATE invoices
SET taxable_amount = COALESCE(subtotal, 0.00),
    grand_total = COALESCE(total_amount, 0.00)
WHERE grand_total = 0.00;
