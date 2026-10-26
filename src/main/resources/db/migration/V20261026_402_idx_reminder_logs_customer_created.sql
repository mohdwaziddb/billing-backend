-- Reminder history modal ("where customer_id = ? order by created_at desc") aur Outstanding list ka
-- latest-reminder subquery ("max(created_at) ... where customer_id = ?") dono index se chalein.
CREATE INDEX idx_reminder_logs_customer_created ON reminder_logs (customer_id, created_at);
