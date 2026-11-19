ALTER TABLE trader_account_transaction ADD COLUMN loan_id BIGINT REFERENCES loan(id);
ALTER TABLE trader_account_transaction ADD COLUMN execution_fee_amount NUMERIC(24,6);

-- Non-cash allocation: collateral already funded the total purchase, including this fee.
INSERT INTO trader_account_transaction
 (organization_id, loan_id, type, amount, execution_fee_amount, balance_after, reference, created_at)
SELECT s.organization_id, s.loan_id, 'BUY_IN_EXECUTION_FEE', 0, s.execution_costs,
 a.available, s.reference, CURRENT_TIMESTAMP
FROM buyin_closure_summary s JOIN trader_account a ON a.organization_id=s.organization_id
WHERE s.execution_costs > 0 AND NOT EXISTS (
 SELECT 1 FROM trader_account_transaction t WHERE t.organization_id=s.organization_id
 AND t.loan_id=s.loan_id AND t.type='BUY_IN_EXECUTION_FEE' AND t.reference=s.reference);
