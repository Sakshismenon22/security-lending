-- Repair projections only where an exact confirmed payment proves settlement.
-- No cash movement, new payment or loan-closure inference is performed.
UPDATE lending_fee
SET status = 'PAID',
    paid_at = COALESCE(paid_at, (SELECT MAX(p.confirmed_at) FROM payment_request p
      WHERE p.fee_id = lending_fee.id AND p.organization_id = lending_fee.organization_id
        AND p.type = 'FEE' AND p.status = 'CONFIRMED' AND p.amount = lending_fee.fee_amount))
WHERE status IN ('DUE', 'PAYMENT_PENDING')
  AND EXISTS (SELECT 1 FROM payment_request p
    WHERE p.fee_id = lending_fee.id AND p.organization_id = lending_fee.organization_id
      AND p.type = 'FEE' AND p.status = 'CONFIRMED' AND p.amount = lending_fee.fee_amount);

UPDATE rebate
SET status = 'PAID'
WHERE status = 'ACCRUED'
  AND EXISTS (SELECT 1 FROM rebate_payment p
    WHERE p.rebate_id = rebate.id AND p.organization_id = rebate.organization_id
      AND p.loan_id = rebate.loan_id AND p.amount = rebate.amount);
