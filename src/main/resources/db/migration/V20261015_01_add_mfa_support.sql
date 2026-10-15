-- target: tenant
-- MFA support: per-company master switch + per-user authenticator devices.
-- Additive only: auto-DDL bhi yehi banata hai, ye file live (validate mode) ke liye hai.
-- Runner duplicate column/table ko tolerate karta hai (already exists -> skip).
ALTER TABLE companies ADD COLUMN is_apply_mfa bit(1) NOT NULL DEFAULT b'0';
CREATE TABLE IF NOT EXISTS `user_mfa_devices` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `device_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `secret_encrypted` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `enabled` bit(1) NOT NULL,
  `failed_attempts` int NOT NULL DEFAULT 0,
  `locked_until` datetime(6) DEFAULT NULL,
  `enrolled_at` datetime(6) DEFAULT NULL,
  `last_used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mfa_user` (`user_id`),
  KEY `idx_mfa_user_enabled` (`user_id`, `enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
