-- DROP company_id from all tenant tables (DATABASE-per-tenant, TSM-like).
-- company_id ka koi kaam nahi, direct tenant connection se data ayega.
-- Usage: USE <db_name>;  (e.g. USE billing_common;  USE maacreation;)
--        SOURCE sql_fixes/drop_company_id_all_tables.sql;
-- Safe to run multiple times (checks existence before drop).
-- Handles unknown FK/index names via INFORMATION_SCHEMA (Hibernate-generated names).

SET @db = DATABASE();

-- 1) Drop all FOREIGN KEYs involving company_id in this DB
SELECT CONCAT('ALTER TABLE `', TABLE_NAME, '` DROP FOREIGN KEY `', CONSTRAINT_NAME, '`;')
FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = @db AND COLUMN_NAME = 'company_id' AND REFERENCED_TABLE_NAME IS NOT NULL
INTO @fk_drops;
-- Fallback: loop via procedure (for MySQL clients that support it)
DELIMITER $$
DROP PROCEDURE IF EXISTS drop_company_id_fks$$
CREATE PROCEDURE drop_company_id_fks()
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE tname VARCHAR(255);
  DECLARE cname VARCHAR(255);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME, CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'company_id' AND REFERENCED_TABLE_NAME IS NOT NULL;
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname, cname;
    IF done THEN LEAVE read_loop; END IF;
    SET @sql = CONCAT('ALTER TABLE `', tname, '` DROP FOREIGN KEY `', cname, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;
CALL drop_company_id_fks();
DROP PROCEDURE IF EXISTS drop_company_id_fks;

-- 2) Drop all secondary INDEXes involving company_id (keep PRIMARY)
DELIMITER $$
DROP PROCEDURE IF EXISTS drop_company_id_indexes$$
CREATE PROCEDURE drop_company_id_indexes()
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE tname VARCHAR(255);
  DECLARE iname VARCHAR(255);
  DECLARE cur CURSOR FOR
    SELECT DISTINCT TABLE_NAME, INDEX_NAME FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'company_id' AND INDEX_NAME <> 'PRIMARY';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname, iname;
    IF done THEN LEAVE read_loop; END IF;
    SET @sql = CONCAT('ALTER TABLE `', tname, '` DROP INDEX `', iname, '`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;
CALL drop_company_id_indexes();
DROP PROCEDURE IF EXISTS drop_company_id_indexes;

-- 3) Drop company_id column from all 32 tenant tables (if exists)
DELIMITER $$
DROP PROCEDURE IF EXISTS drop_company_id_columns$$
CREATE PROCEDURE drop_company_id_columns()
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE tname VARCHAR(255);
  DECLARE cur CURSOR FOR
    SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'company_id';
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = TRUE;
  OPEN cur;
  read_loop: LOOP
    FETCH cur INTO tname;
    IF done THEN LEAVE read_loop; END IF;
    SET @sql = CONCAT('ALTER TABLE `', tname, '` DROP COLUMN `company_id`');
    PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END LOOP;
  CLOSE cur;
END$$
DELIMITER ;
CALL drop_company_id_columns();
DROP PROCEDURE IF EXISTS drop_company_id_columns;

-- 4) Verify: should return zero rows
SELECT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'company_id';

-- Expected tables cleaned (for reference, fresh entities have NO company_id):
-- audit_logs, company_invoice_settings, company_theme_settings, customers, email_logs,
-- email_provider_settings, email_templates, expenses, expense_categories, inventory_ledger,
-- invoices, invoice_items, invoice_item_allocations, notification_channels, notification_logs,
-- payments, payment_modes, products, product_batches, product_categories, product_sub_categories,
-- purchases, purchase_items, reminder_logs, role_menu_permission, role_menu_action_permission,
-- sms_provider_settings, sms_templates, tax_master, users, user_permission, whatsapp_provider_settings
-- NOTE: companies table NEVER had company_id (it is the parent) - only database_name kept for routing.
