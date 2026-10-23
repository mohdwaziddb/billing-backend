-- target: tenant
-- UPI / Card / Bank Transfer / Cheque par optional Reference No (UTR / approval no.) field dikhe.
UPDATE payment_modes SET requires_reference = 1 WHERE mode_code IN ('UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE');
