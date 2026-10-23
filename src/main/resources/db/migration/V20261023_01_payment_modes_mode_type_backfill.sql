-- target: tenant
-- Payment modes: purani rows OFFLINE; ONLINE code wala mode gateway mode hai.
UPDATE payment_modes SET mode_type = CASE WHEN mode_code = 'ONLINE' THEN 'ONLINE' ELSE 'OFFLINE' END WHERE mode_type IS NULL;
