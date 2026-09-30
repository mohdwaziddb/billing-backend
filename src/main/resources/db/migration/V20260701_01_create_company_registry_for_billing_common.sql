-- Billing common catalog for DATABASE-per-tenant (subdomain -> database_name mapping)
CREATE DATABASE IF NOT EXISTS billing_common;

CREATE TABLE IF NOT EXISTS billing_common.company_registry (
    company_code VARCHAR(50) NOT NULL PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    database_name VARCHAR(100) NOT NULL UNIQUE,
    domain VARCHAR(255) UNIQUE,
    db_host VARCHAR(100) NOT NULL DEFAULT 'localhost:3306',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed for local development (TSM localhost:9009 pattern)
INSERT IGNORE INTO billing_common.company_registry (company_code, company_name, database_name, domain, db_host, status)
VALUES ('ACME', 'Acme Demo', 'billing_company_acme', 'acme.localhost:9009', 'localhost:3306', 'ACTIVE'),
       ('ACME_WEB', 'Acme Demo Web', 'billing_company_acme', 'acme.localhost:5173', 'localhost:3306', 'ACTIVE'),
       ('DEFAULT', 'Default Company', 'billing_common', 'localhost:9009', 'localhost:3306', 'ACTIVE'),
       ('DEFAULT_WEB', 'Default Company Web', 'billing_common', 'localhost:5173', 'localhost:3306', 'ACTIVE');
