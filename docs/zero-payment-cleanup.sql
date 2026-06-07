-- Identify zero/non-positive payment rows that currently appear as PAYMENT ledger entries.
SELECT p.id,
       p.company_id,
       p.customer_id,
       p.invoice_id,
       p.amount,
       p.payment_date,
       p.mode,
       p.remarks,
       p.created_at
FROM payments p
WHERE p.amount IS NULL OR p.amount <= 0;

-- Remove invalid payment rows after review.
-- These rows have no business value and should not affect customer/invoice balances.
DELETE FROM payments
WHERE amount IS NULL OR amount <= 0;

-- If your environment has a separate transaction/ledger table, review first:
-- SELECT *
-- FROM ledger_transactions
-- WHERE transaction_type = 'PAYMENT'
--   AND (amount IS NULL OR amount <= 0);

-- Then remove only those invalid ledger transaction rows:
-- DELETE FROM ledger_transactions
-- WHERE transaction_type = 'PAYMENT'
--   AND (amount IS NULL OR amount <= 0);
