CREATE TABLE IF NOT EXISTS app_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  menu_name VARCHAR(255) NOT NULL,
  menu_code VARCHAR(255) NOT NULL,
  menu_icon VARCHAR(255),
  menu_route VARCHAR(255) NOT NULL,
  display_order INT NOT NULL,
  parent_menu_id BIGINT,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT fk_app_menu_parent FOREIGN KEY (parent_menu_id) REFERENCES app_menu(id),
  CONSTRAINT uk_app_menu_code UNIQUE (menu_code)
);

CREATE TABLE IF NOT EXISTS app_menu_action (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  app_menu_id BIGINT NOT NULL,
  action_name VARCHAR(255) NOT NULL,
  action_code VARCHAR(255) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT fk_action_menu FOREIGN KEY (app_menu_id) REFERENCES app_menu(id),
  CONSTRAINT uk_menu_action_code UNIQUE (app_menu_id, action_code)
);

CREATE TABLE IF NOT EXISTS role_master (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_name VARCHAR(255) NOT NULL,
  role_code VARCHAR(255) NOT NULL,
  is_system_role BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT uk_role_master_code UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS role_menu_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  app_menu_id BIGINT NOT NULL,
  can_view BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT fk_role_menu_role FOREIGN KEY (role_id) REFERENCES role_master(id),
  CONSTRAINT fk_role_menu_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT fk_role_menu_menu FOREIGN KEY (app_menu_id) REFERENCES app_menu(id),
  CONSTRAINT uk_role_menu_permission UNIQUE (company_id, role_id, app_menu_id)
);

CREATE TABLE IF NOT EXISTS role_menu_action_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  app_menu_id BIGINT NOT NULL,
  app_menu_action_id BIGINT NOT NULL,
  is_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT fk_role_action_role FOREIGN KEY (role_id) REFERENCES role_master(id),
  CONSTRAINT fk_role_action_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT fk_role_action_menu FOREIGN KEY (app_menu_id) REFERENCES app_menu(id),
  CONSTRAINT fk_role_action_action FOREIGN KEY (app_menu_action_id) REFERENCES app_menu_action(id),
  CONSTRAINT uk_role_menu_action_permission UNIQUE (company_id, role_id, app_menu_id, app_menu_action_id)
);

CREATE TABLE IF NOT EXISTS user_permission (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  company_id BIGINT NOT NULL,
  app_menu_id BIGINT NOT NULL,
  app_menu_action_id BIGINT NOT NULL,
  is_allowed BOOLEAN NOT NULL DEFAULT FALSE,
  created_at DATETIME(6) NOT NULL,
  created_by VARCHAR(255),
  updated_at DATETIME(6) NOT NULL,
  updated_by VARCHAR(255),
  CONSTRAINT fk_user_permission_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_permission_company FOREIGN KEY (company_id) REFERENCES companies(id),
  CONSTRAINT fk_user_permission_menu FOREIGN KEY (app_menu_id) REFERENCES app_menu(id),
  CONSTRAINT fk_user_permission_action FOREIGN KEY (app_menu_action_id) REFERENCES app_menu_action(id),
  CONSTRAINT uk_user_menu_action_permission UNIQUE (company_id, user_id, app_menu_id, app_menu_action_id)
);
