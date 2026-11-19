ALTER TABLE lender_account_ledger ADD COLUMN execution_fee_amount NUMERIC(24,6);

-- Fee allocations are included in existing purchase/payable amounts, never added again.
UPDATE lender_account_ledger
SET execution_fee_amount = (SELECT b.costs FROM buy_in b
 WHERE b.organization_id = lender_account_ledger.organization_id
   AND b.loan_id = lender_account_ledger.loan_id
   AND b.inventory_reference = lender_account_ledger.reference)
WHERE type = 'BUY_IN_PURCHASE_PAYABLE'
 AND EXISTS (SELECT 1 FROM buy_in b
   WHERE b.organization_id = lender_account_ledger.organization_id
     AND b.loan_id = lender_account_ledger.loan_id
     AND b.inventory_reference = lender_account_ledger.reference);

UPDATE lender_account_ledger
SET execution_fee_amount = (SELECT c.purchase_cost -
 COALESCE((SELECT SUM(b.price * b.closeout_quantity) FROM buy_in b
   WHERE b.organization_id=c.organization_id AND b.loan_id=c.loan_id AND b.closeout_quantity>0), 0)
 FROM buyin_loan_closeout c
 WHERE c.organization_id=lender_account_ledger.organization_id
   AND c.loan_id=lender_account_ledger.loan_id AND c.reference=lender_account_ledger.reference)
WHERE type='BUY_IN_PAYABLE_SETTLED'
 AND EXISTS (SELECT 1 FROM buyin_loan_closeout c
   WHERE c.organization_id=lender_account_ledger.organization_id
     AND c.loan_id=lender_account_ledger.loan_id AND c.reference=lender_account_ledger.reference);
