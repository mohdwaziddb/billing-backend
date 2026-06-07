ALTER TABLE app_menu
  ADD COLUMN parent_menu_id BIGINT NULL AFTER display_order;

ALTER TABLE app_menu
  MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE app_menu_action
  MODIFY COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE role_master
  MODIFY COLUMN is_system_role BOOLEAN NOT NULL DEFAULT TRUE;

ALTER TABLE role_menu_permission
  MODIFY COLUMN can_view BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE role_menu_action_permission
  MODIFY COLUMN is_allowed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_permission
  MODIFY COLUMN is_allowed BOOLEAN NOT NULL DEFAULT FALSE;

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, is_active, created_at, updated_at)
VALUES ('Setup', 'SETUP', 'Settings', '/setup', 9, TRUE, NOW(6), NOW(6));

UPDATE app_menu users
JOIN app_menu setup ON setup.menu_code = 'SETUP'
SET users.menu_name = 'Users',
    users.menu_icon = 'Users',
    users.menu_route = '/setup/users',
    users.display_order = 10,
    users.parent_menu_id = setup.id
WHERE users.menu_code = 'USERS';

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'Role Permissions', 'ROLE_PERMISSIONS', 'ShieldCheck', '/setup/role-permissions', 11, setup.id, TRUE, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

ALTER TABLE app_menu
  ADD CONSTRAINT fk_app_menu_parent FOREIGN KEY (parent_menu_id) REFERENCES app_menu(id);

INSERT IGNORE INTO app_menu_action (app_menu_id, action_name, action_code, is_active, created_at, updated_at)
SELECT m.id, a.action_name, a.action_code, TRUE, NOW(6), NOW(6)
FROM app_menu m
JOIN (
    SELECT 'View' action_name, 'VIEW' action_code
    UNION ALL SELECT 'Add', 'ADD'
    UNION ALL SELECT 'Edit', 'EDIT'
    UNION ALL SELECT 'Delete', 'DELETE'
    UNION ALL SELECT 'Export', 'EXPORT'
) a
WHERE m.menu_code IN ('SETUP', 'ROLE_PERMISSIONS');

INSERT IGNORE INTO role_menu_permission (role_id, company_id, app_menu_id, can_view, created_at, updated_at)
SELECT r.id,
       c.id,
       m.id,
       CASE WHEN r.role_code = 'OWNER' THEN TRUE ELSE FALSE END,
       NOW(6),
       NOW(6)
FROM role_master r
CROSS JOIN companies c
JOIN app_menu m ON m.menu_code IN ('SETUP', 'ROLE_PERMISSIONS');

INSERT IGNORE INTO role_menu_action_permission (role_id, company_id, app_menu_id, app_menu_action_id, is_allowed, created_at, updated_at)
SELECT r.id,
       c.id,
       m.id,
       a.id,
       CASE WHEN r.role_code = 'OWNER' THEN TRUE ELSE FALSE END,
       NOW(6),
       NOW(6)
FROM role_master r
CROSS JOIN companies c
JOIN app_menu m ON m.menu_code IN ('SETUP', 'ROLE_PERMISSIONS')
JOIN app_menu_action a ON a.app_menu_id = m.id;
