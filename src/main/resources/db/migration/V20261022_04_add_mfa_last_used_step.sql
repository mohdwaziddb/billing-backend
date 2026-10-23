-- target: tenant
-- TOTP code replay protection.
ALTER TABLE user_mfa_devices ADD COLUMN last_used_step bigint DEFAULT NULL;
