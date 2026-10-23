-- target: tenant
-- Sirf Cash + Online (Razorpay) rakhe; baaki purane default modes delete.
-- Safe: payments.mode sirf code string hai (koi FK nahi), purani payments "UPI"/"CARD" hi dikhati rahengi.
DELETE FROM payment_modes WHERE mode_code IN ('UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'WALLET', 'OTHER');
