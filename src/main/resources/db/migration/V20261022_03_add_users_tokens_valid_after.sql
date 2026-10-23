-- target: tenant
-- Password reset/deactivate ke baad purane access tokens band (ek statement per file:
-- runner "already exists" par baaki statements skip karta hai).
ALTER TABLE users ADD COLUMN tokens_valid_after datetime(6) DEFAULT NULL;
