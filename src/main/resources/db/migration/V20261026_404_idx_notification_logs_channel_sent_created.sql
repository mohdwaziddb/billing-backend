-- SMS / WhatsApp / Mail History pages: "where channel = ? order by sent_at desc, created_at desc" + LIMIT.
-- Alag-alag channel / sent_at index se poori channel ki rows filesort hoti thi.
CREATE INDEX idx_notification_logs_channel_sent_created ON notification_logs (channel, sent_at, created_at);
