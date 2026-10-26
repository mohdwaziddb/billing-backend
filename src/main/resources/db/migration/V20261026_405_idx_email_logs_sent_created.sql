-- Mail History page: email_logs "order by sent_at desc, created_at desc" + LIMIT
-- (sirf sent_at wala index is do-column order ko filesort se nahi bachata).
CREATE INDEX idx_email_logs_sent_created ON email_logs (sent_at, created_at);
