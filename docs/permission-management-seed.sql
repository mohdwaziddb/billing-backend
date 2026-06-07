INSERT IGNORE INTO role_master (role_name, role_code, is_system_role, created_at, updated_at)
VALUES ('Owner', 'OWNER', 1, NOW(6), NOW(6)),
       ('Admin', 'ADMIN', 1, NOW(6), NOW(6)),
       ('User', 'USER', 1, NOW(6), NOW(6));

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
VALUES ('Dashboard', 'DASHBOARD', 'LayoutDashboard', '/dashboard', 1, NULL, 1, NOW(6), NOW(6)),
       ('Customers', 'CUSTOMERS', 'Users', '/customers', 2, NULL, 1, NOW(6), NOW(6)),
       ('Products', 'PRODUCTS', 'Boxes', '/products', 3, NULL, 1, NOW(6), NOW(6)),
       ('Create Invoice', 'CREATE_INVOICE', 'FilePlus2', '/create-invoice', 4, NULL, 1, NOW(6), NOW(6)),
       ('Invoices', 'INVOICES', 'FileText', '/invoices', 5, NULL, 1, NOW(6), NOW(6)),
       ('Payments', 'PAYMENTS', 'CreditCard', '/payments', 6, NULL, 1, NOW(6), NOW(6)),
       ('Outstanding', 'OUTSTANDING', 'Wallet', '/outstanding', 7, NULL, 1, NOW(6), NOW(6)),
       ('Analytics', 'ANALYTICS', 'BarChart3', '/analytics', 8, NULL, 1, NOW(6), NOW(6)),
       ('Setup', 'SETUP', 'Settings', '/setup', 9, NULL, 1, NOW(6), NOW(6));

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'Users', 'USERS', 'Users', '/setup/users', 10, setup.id, 1, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'Role Permissions', 'ROLE_PERMISSIONS', 'ShieldCheck', '/setup/role-permissions', 11, setup.id, 1, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

INSERT IGNORE INTO app_menu (menu_name, menu_code, menu_icon, menu_route, display_order, parent_menu_id, is_active, created_at, updated_at)
SELECT 'Product Categories', 'PRODUCT_CATEGORY', 'Tags', '/setup/product-categories', 11, setup.id, 1, NOW(6), NOW(6)
FROM app_menu setup
WHERE setup.menu_code = 'SETUP';

UPDATE app_menu product_category
JOIN app_menu setup ON setup.menu_code = 'SETUP'
SET product_category.menu_name = 'Product Categories',
    product_category.menu_icon = 'Tags',
    product_category.menu_route = '/setup/product-categories',
    product_category.display_order = 11,
    product_category.parent_menu_id = setup.id
WHERE product_category.menu_code = 'PRODUCT_CATEGORY';

UPDATE app_menu role_permissions
JOIN app_menu setup ON setup.menu_code = 'SETUP'
SET role_permissions.display_order = 12,
    role_permissions.parent_menu_id = setup.id
WHERE role_permissions.menu_code = 'ROLE_PERMISSIONS';

UPDATE app_menu users
JOIN app_menu setup ON setup.menu_code = 'SETUP'
SET users.menu_name = 'Users',
    users.menu_icon = 'Users',
    users.menu_route = '/setup/users',
    users.display_order = 10,
    users.parent_menu_id = setup.id
WHERE users.menu_code = 'USERS';

INSERT IGNORE INTO app_menu_action (app_menu_id, action_name, action_code, is_active, created_at, updated_at)
SELECT m.id, a.action_name, a.action_code, 1, NOW(6), NOW(6)
FROM app_menu m
JOIN (
    SELECT 'View' action_name, 'VIEW' action_code
    UNION ALL SELECT 'Add', 'ADD'
    UNION ALL SELECT 'Edit', 'EDIT'
    UNION ALL SELECT 'Delete', 'DELETE'
    UNION ALL SELECT 'Export', 'EXPORT'
) a;

INSERT IGNORE INTO role_menu_permission (role_id, company_id, app_menu_id, can_view, created_at, updated_at)
SELECT r.id,
       c.id,
       m.id,
       CASE
           WHEN r.role_code = 'OWNER' THEN 1
           WHEN r.role_code = 'ADMIN' AND m.menu_code NOT IN ('SETUP', 'USERS', 'ROLE_PERMISSIONS') THEN 1
           WHEN r.role_code = 'USER' AND m.menu_code IN ('DASHBOARD', 'CUSTOMERS', 'PRODUCTS', 'CREATE_INVOICE', 'INVOICES', 'OUTSTANDING', 'ANALYTICS') THEN 1
           ELSE 0
       END,
       NOW(6),
       NOW(6)
FROM role_master r
CROSS JOIN companies c
CROSS JOIN app_menu m;

UPDATE role_menu_permission permission
JOIN role_master r ON r.id = permission.role_id
JOIN app_menu m ON m.id = permission.app_menu_id
SET permission.can_view = CASE WHEN r.role_code = 'ADMIN' THEN 1 ELSE 0 END,
    permission.updated_at = NOW(6)
WHERE m.menu_code = 'PRODUCT_CATEGORY'
  AND r.role_code <> 'OWNER';

INSERT IGNORE INTO role_menu_action_permission (role_id, company_id, app_menu_id, app_menu_action_id, is_allowed, created_at, updated_at)
SELECT r.id,
       c.id,
       m.id,
       a.id,
       CASE
           WHEN r.role_code = 'OWNER' THEN 1
           WHEN r.role_code = 'ADMIN' AND m.menu_code = 'PRODUCT_CATEGORY' AND a.action_code = 'VIEW' THEN 1
           WHEN r.role_code = 'ADMIN' AND m.menu_code NOT IN ('SETUP', 'USERS', 'ROLE_PERMISSIONS', 'PRODUCT_CATEGORY') THEN 1
           WHEN r.role_code = 'USER' AND a.action_code = 'VIEW' AND m.menu_code IN ('DASHBOARD', 'CUSTOMERS', 'PRODUCTS', 'CREATE_INVOICE', 'INVOICES', 'OUTSTANDING', 'ANALYTICS') THEN 1
           WHEN r.role_code = 'USER' AND m.menu_code = 'CREATE_INVOICE' AND a.action_code = 'ADD' THEN 1
           ELSE 0
       END,
       NOW(6),
       NOW(6)
FROM role_master r
CROSS JOIN companies c
CROSS JOIN app_menu m
JOIN app_menu_action a ON a.app_menu_id = m.id;

UPDATE role_menu_action_permission permission
JOIN role_master r ON r.id = permission.role_id
JOIN app_menu m ON m.id = permission.app_menu_id
JOIN app_menu_action a ON a.id = permission.app_menu_action_id
SET permission.is_allowed = CASE WHEN a.action_code = 'VIEW' THEN 1 ELSE 0 END,
    permission.updated_at = NOW(6)
WHERE m.menu_code = 'PRODUCT_CATEGORY'
  AND r.role_code <> 'OWNER';
