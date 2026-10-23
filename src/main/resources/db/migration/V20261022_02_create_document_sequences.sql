-- target: tenant
-- Invoice/purchase number sequence (INV/2627/00001). Boot auto-DDL bhi banata hai;
-- ye backup hai taaki auto-DDL fail ho to bhi invoice create na ruke.
CREATE TABLE IF NOT EXISTS document_sequences (
  id bigint NOT NULL AUTO_INCREMENT,
  doc_type varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  period_key varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  last_number bigint NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_document_sequences_type_period (doc_type, period_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
