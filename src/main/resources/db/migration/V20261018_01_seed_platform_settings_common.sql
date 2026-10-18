-- target: common
-- Seed the default platform-admin row so a fresh deploy can open Platform
-- Admin without manual SQL. Idempotent: inserts only while the table is
-- empty and never overwrites existing credentials.
INSERT INTO billing_common.platform_settings
    (created_at, updated_at, platform_name, platform_tagline, username, password)
SELECT NOW(6), NOW(6), 'Bizio', 'Empowering Businesses, Simplifying Growth', 'mohdwaziddb',
       '$2a$10$5wDYeNVO0UfVWDXHgKvID.FLjfT6VPn3ElBO1oRSZ1cUZWbK3r0YW'
WHERE NOT EXISTS (SELECT 1 FROM billing_common.platform_settings);
