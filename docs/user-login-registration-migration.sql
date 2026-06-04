-- Run after a full backup.
-- Adjust DROP INDEX statements if your existing MySQL index names differ.

ALTER TABLE users
    ADD COLUMN mobile_number VARCHAR(255) NULL;

-- If your existing users table still has the old `email` column, rename it to `email_id`.
-- Skip this statement if the column is already named `email_id`.
ALTER TABLE users
    CHANGE COLUMN email email_id VARCHAR(255) NOT NULL;

-- Backfill real mobile numbers before making the column mandatory.
-- UPDATE users SET mobile_number = '<unique_mobile_number>' WHERE id = <user_id>;

ALTER TABLE users
    MODIFY mobile_number VARCHAR(255) NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT uk_users_mobile_number UNIQUE (mobile_number),
    ADD CONSTRAINT uk_users_email_id UNIQUE (email_id);
