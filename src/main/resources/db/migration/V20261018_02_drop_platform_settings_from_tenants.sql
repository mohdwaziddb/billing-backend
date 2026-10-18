-- target: tenant
-- platform_settings lives in the common catalog (billing_common) only, and
-- tenant copies are unused legacy. Drop them so tenant databases no longer
-- carry the table at all.
DROP TABLE IF EXISTS platform_settings;
