-- target: tenant
-- Mail History me purani rows ka provider "EMAIL" (channel ka naam) save hua tha.
-- Jis row ka sender (From) kisi email provider ke sender_email se milta hai, uska asli provider bharo.
-- Jinka sender khaali hai wo waise hi rahengi (UI unhe "Not recorded" dikhata hai) — andaza nahi lagate.
UPDATE notification_logs n
JOIN email_provider_settings e ON LOWER(TRIM(e.sender_email)) = LOWER(TRIM(n.sender))
SET n.provider_name = e.provider_name
WHERE n.channel = 'EMAIL'
  AND (n.provider_name IS NULL OR n.provider_name = '' OR UPPER(n.provider_name) = 'EMAIL')
  AND n.sender IS NOT NULL AND n.sender <> '';
