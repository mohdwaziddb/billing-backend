-- Billing common catalog for DATABASE-per-tenant (subdomain -> database_name mapping)
-- Catalog holds ONLY: company_registry + platform_settings. All business tables live in tenant DBs.
CREATE DATABASE IF NOT EXISTS billing_common;

CREATE TABLE IF NOT EXISTS billing_common.company_registry (
    company_code VARCHAR(50) NOT NULL PRIMARY KEY,
    company_name VARCHAR(150) NOT NULL,
    database_name VARCHAR(100) NOT NULL UNIQUE,
    domain VARCHAR(255) UNIQUE,
    db_host VARCHAR(100) NOT NULL DEFAULT 'localhost:3306',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    company_id BIGINT NULL COMMENT 'id of the company row inside its own tenant database (for platform-admin routing)',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Global platform credentials/branding (platform-admin works on the main domain without a tenant pool).
CREATE TABLE IF NOT EXISTS billing_common.platform_settings (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    created_at DATETIME(6) NOT NULL,
    created_by VARCHAR(255) DEFAULT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by VARCHAR(255) DEFAULT NULL,
    password VARCHAR(255) DEFAULT NULL,
    platform_logo VARCHAR(255) DEFAULT NULL,
    platform_name VARCHAR(255) NOT NULL,
    platform_tagline VARCHAR(255) DEFAULT NULL,
    username VARCHAR(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed for local development (dynamic per subdomain)
-- billing_common is catalog only; default local tenant is maacreation (TSM-like).
-- NOTE: database_name is UNIQUE -> exactly one row per tenant database.
-- Local resolution for localhost:5173/9009 and maacreation.localhost comes from
-- CompanyDomainInfo static map + X-Company-Code header, not from extra rows.
INSERT IGNORE INTO billing_common.company_registry (company_code, company_name, database_name, domain, db_host, status)
VALUES ('MAACREATION', 'Maa Creation', 'maacreation', 'maacreation.biziotechnologies.com', 'localhost:3306', 'ACTIVE');
