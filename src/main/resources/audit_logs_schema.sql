CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT NOT NULL AUTO_INCREMENT,
  company_id BIGINT NOT NULL,
  module_name VARCHAR(255) NOT NULL,
  entity_name VARCHAR(255) NOT NULL,
  entity_id BIGINT NOT NULL,
  action_type VARCHAR(255) NOT NULL,
  old_data JSON NULL,
  new_data JSON NULL,
  changed_fields JSON NULL,
  user_id BIGINT NULL,
  user_name VARCHAR(255) NULL,
  ip_address VARCHAR(255) NULL,
  user_agent VARCHAR(1024) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_audit_logs_company FOREIGN KEY (company_id) REFERENCES companies (id)
);

CREATE INDEX idx_audit_company ON audit_logs (company_id);
CREATE INDEX idx_audit_entity ON audit_logs (entity_id);
CREATE INDEX idx_audit_module ON audit_logs (module_name);
CREATE INDEX idx_audit_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_user ON audit_logs (user_id);
