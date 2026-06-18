ALTER TABLE companies
    ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

UPDATE companies
SET is_active = TRUE
WHERE is_active IS NULL;

ALTER TABLE users
    DROP INDEX uk_users_mobile_number,
    DROP INDEX uk_users_email_id;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(255) NULL;

UPDATE users
SET username = employee_code
WHERE (username IS NULL OR username = '')
  AND employee_code IS NOT NULL
  AND employee_code <> '';

UPDATE users
SET username = CONCAT('user', id)
WHERE username IS NULL OR username = '';

ALTER TABLE users
    MODIFY username VARCHAR(255) NOT NULL;

ALTER TABLE users
    MODIFY company_id BIGINT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_company_email UNIQUE (company_id, email_id),
    ADD CONSTRAINT uk_users_company_mobile UNIQUE (company_id, mobile_number),
    ADD CONSTRAINT uk_users_company_username UNIQUE (company_id, username);

ALTER TABLE users
    DROP COLUMN employee_code;
