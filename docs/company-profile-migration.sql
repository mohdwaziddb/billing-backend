ALTER TABLE companies
  ADD COLUMN legal_name VARCHAR(255) NULL AFTER name,
  ADD COLUMN alternate_phone VARCHAR(255) NULL AFTER phone,
  ADD COLUMN address_line_1 VARCHAR(255) NULL AFTER address,
  ADD COLUMN address_line_2 VARCHAR(255) NULL AFTER address_line_1,
  ADD COLUMN city VARCHAR(255) NULL AFTER address_line_2,
  ADD COLUMN state VARCHAR(255) NULL AFTER city,
  ADD COLUMN country VARCHAR(255) NULL AFTER state,
  ADD COLUMN pincode VARCHAR(255) NULL AFTER country,
  ADD COLUMN pan_number VARCHAR(255) NULL AFTER tax_id,
  ADD COLUMN cin_number VARCHAR(255) NULL AFTER pan_number,
  ADD COLUMN logo_url VARCHAR(255) NULL AFTER cin_number,
  ADD COLUMN website_url VARCHAR(255) NULL AFTER logo_url;

CREATE TABLE IF NOT EXISTS company_theme_settings (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  theme_color VARCHAR(7) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255) NULL,
  updated_by VARCHAR(255) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_company_theme_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT uk_company_theme_company UNIQUE (company_id)
);

CREATE TABLE IF NOT EXISTS company_owners (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255) NULL,
  updated_by VARCHAR(255) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_company_owner_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT fk_company_owner_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT uk_company_owner UNIQUE (company_id, user_id)
);

INSERT IGNORE INTO company_theme_settings (company_id, theme_color, created_at, updated_at)
SELECT id, '#0EA5E9', NOW(6), NOW(6)
FROM companies;

INSERT IGNORE INTO company_owners (company_id, user_id, created_at, updated_at)
SELECT company_id, id, NOW(6), NOW(6)
FROM users
WHERE role = 'OWNER'
  AND company_id IS NOT NULL;

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'Theme Settings', 'THEME_SETTINGS', 'Palette', '/setup/theme-settings', 12, setup.id, 1, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'About Company', 'ABOUT_COMPANY', 'Building2', '/setup/about-company', 13, setup.id, 1, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

INSERT IGNORE INTO app_menu_action (app_menu_id, action_name, action_code, is_active, created_at, updated_at)
SELECT m.id, actions.action_name, actions.action_code, 1, NOW(6), NOW(6)
FROM app_menu m
JOIN (
  SELECT 'View' action_name, 'VIEW' action_code
  UNION ALL SELECT 'Add', 'ADD'
  UNION ALL SELECT 'Edit', 'EDIT'
  UNION ALL SELECT 'Delete', 'DELETE'
  UNION ALL SELECT 'Export', 'EXPORT'
) actions
WHERE m.menu_code IN ('THEME_SETTINGS', 'ABOUT_COMPANY');

INSERT IGNORE INTO role_menu_permission (role_id, company_id, app_menu_id, can_view, created_at, updated_at)
SELECT r.id, c.id, m.id,
       CASE
         WHEN r.role_code = 'OWNER' THEN 1
         WHEN r.role_code IN ('ADMIN', 'USER') AND m.menu_code = 'ABOUT_COMPANY' THEN 1
         ELSE 0
       END,
       NOW(6), NOW(6)
FROM role_master r
CROSS JOIN companies c
JOIN app_menu m ON m.menu_code IN ('THEME_SETTINGS', 'ABOUT_COMPANY');

INSERT IGNORE INTO role_menu_action_permission (role_id, company_id, app_menu_id, app_menu_action_id, is_allowed, created_at, updated_at)
SELECT r.id, c.id, m.id, a.id,
       CASE
         WHEN r.role_code = 'OWNER' THEN 1
         WHEN r.role_code IN ('ADMIN', 'USER') AND m.menu_code = 'ABOUT_COMPANY' AND a.action_code = 'VIEW' THEN 1
         ELSE 0
       END,
       NOW(6), NOW(6)
FROM role_master r
CROSS JOIN companies c
JOIN app_menu m ON m.menu_code IN ('THEME_SETTINGS', 'ABOUT_COMPANY')
JOIN app_menu_action a ON a.app_menu_id = m.id;
