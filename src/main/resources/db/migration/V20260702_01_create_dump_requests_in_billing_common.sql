-- target: common
-- Platform DB Dump Manager: queued dump jobs (RDS-style, EC2 MySQL).
-- Lives in billing_common (platform-level, like company_registry): no tenant switch needed.
-- NOTE: auto-applied on boot by TenantSchemaMigrationService (schema_version tracked). Files live on server disk (app.dump.dir), never web root.
CREATE TABLE IF NOT EXISTS billing_common.dump_requests (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    company_code VARCHAR(50) NULL COMMENT 'registry code when the DB belongs to a tenant, NULL for billing_common itself',
    database_name VARCHAR(100) NOT NULL,
    db_host VARCHAR(100) NOT NULL DEFAULT 'localhost:3306',
    mode VARCHAR(20) NOT NULL DEFAULT 'FULL' COMMENT 'FULL | INCLUDE | EXCLUDE',
    tables TEXT NULL COMMENT 'comma-separated table list for INCLUDE/EXCLUDE modes',
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED' COMMENT 'QUEUED | RUNNING | COMPLETED | FAILED',
    requested_by VARCHAR(255) NULL COMMENT 'platform-admin username',
    file_path VARCHAR(500) NULL COMMENT 'server-side dump file; NULL when expired/deleted',
    file_size BIGINT NULL DEFAULT 0,
    error TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    finished_at DATETIME NULL,
    INDEX idx_dump_status_created (status, created_at),
    INDEX idx_dump_db_status (database_name, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
