ALTER TABLE role_menu_permission
  ADD COLUMN company_id BIGINT NULL AFTER role_id;

ALTER TABLE role_menu_action_permission
  ADD COLUMN company_id BIGINT NULL AFTER role_id;

ALTER TABLE user_permission
  ADD COLUMN company_id BIGINT NULL AFTER user_id;

UPDATE user_permission up
JOIN users u ON u.id = up.user_id
SET up.company_id = u.company_id
WHERE up.company_id IS NULL;

INSERT IGNORE INTO role_menu_permission (role_id, company_id, app_menu_id, can_view, created_at, updated_at)
SELECT p.role_id, c.id, p.app_menu_id, p.can_view, NOW(6), NOW(6)
FROM role_menu_permission p
CROSS JOIN companies c
WHERE p.company_id IS NULL;

INSERT IGNORE INTO role_menu_action_permission (role_id, company_id, app_menu_id, app_menu_action_id, is_allowed, created_at, updated_at)
SELECT p.role_id, c.id, p.app_menu_id, p.app_menu_action_id, p.is_allowed, NOW(6), NOW(6)
FROM role_menu_action_permission p
CROSS JOIN companies c
WHERE p.company_id IS NULL;

DELETE FROM role_menu_permission WHERE company_id IS NULL;
DELETE FROM role_menu_action_permission WHERE company_id IS NULL;

ALTER TABLE role_menu_permission
  ADD INDEX idx_role_menu_permission_role (role_id),
  ADD INDEX idx_role_menu_permission_company (company_id),
  MODIFY company_id BIGINT NOT NULL,
  DROP INDEX uk_role_menu_permission,
  ADD CONSTRAINT fk_role_menu_company FOREIGN KEY (company_id) REFERENCES companies(id),
  ADD CONSTRAINT uk_role_menu_permission UNIQUE (company_id, role_id, app_menu_id);

ALTER TABLE role_menu_action_permission
  ADD INDEX idx_role_menu_action_permission_role (role_id),
  ADD INDEX idx_role_menu_action_permission_company (company_id),
  MODIFY company_id BIGINT NOT NULL,
  DROP INDEX uk_role_menu_action_permission,
  ADD CONSTRAINT fk_role_action_company FOREIGN KEY (company_id) REFERENCES companies(id),
  ADD CONSTRAINT uk_role_menu_action_permission UNIQUE (company_id, role_id, app_menu_id, app_menu_action_id);

ALTER TABLE user_permission
  ADD INDEX idx_user_permission_user (user_id),
  ADD INDEX idx_user_permission_company (company_id),
  MODIFY company_id BIGINT NOT NULL,
  DROP INDEX uk_user_menu_action_permission,
  ADD CONSTRAINT fk_user_permission_company FOREIGN KEY (company_id) REFERENCES companies(id),
  ADD CONSTRAINT uk_user_menu_action_permission UNIQUE (company_id, user_id, app_menu_id, app_menu_action_id);
