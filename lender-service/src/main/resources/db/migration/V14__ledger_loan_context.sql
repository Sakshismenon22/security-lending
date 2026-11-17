ALTER TABLE lender_account_ledger ADD COLUMN loan_id BIGINT REFERENCES loan(id);
CREATE INDEX ix_ledger_org_loan ON lender_account_ledger(organization_id,loan_id);

-- Rebate payment event IDs are immutable and unique within the organization.
UPDATE lender_account_ledger SET loan_id = (
 SELECT MIN(p.loan_id) FROM rebate_payment p
 WHERE p.organization_id=lender_account_ledger.organization_id
   AND p.source_event_id=lender_account_ledger.reference
 HAVING COUNT(DISTINCT p.loan_id)=1
) WHERE type='REBATE_PAID';

-- Custody references may be reused: backfill only an unambiguous loan match.
UPDATE lender_account_ledger SET loan_id = (
 SELECT MIN(p.loan_id) FROM collateral_history h JOIN collateral_position p ON p.id=h.position_id
 WHERE h.organization_id=lender_account_ledger.organization_id
   AND p.organization_id=lender_account_ledger.organization_id
   AND h.type=lender_account_ledger.type AND h.reference=lender_account_ledger.reference
   AND lender_account_ledger.amount=CASE WHEN h.type='PLEDGE_CONFIRMED' THEN h.cash_amount ELSE -h.cash_amount END
 HAVING COUNT(DISTINCT p.loan_id)=1
) WHERE type IN ('PLEDGE_CONFIRMED','RELEASE_CONFIRMED') AND loan_id IS NULL;
