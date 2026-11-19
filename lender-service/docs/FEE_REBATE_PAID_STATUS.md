# Fee and rebate paid-status synchronization

This change fixes three separate causes of stale billing status:

- Borrower fee confirmations updated the bill only for standalone PENDING payments.
  Return-linked and post-closure SETTLEMENT_SUBMITTED payments could become CONFIRMED
  while the fee remained DUE or PAYMENT_PENDING. All confirmed fee paths now update
  the bill to PAID and store its paid time. Rejections restore DUE. A repeated fee
  acknowledgement does not release cash or publish the receipt again.
- A partial return reserved fees, but its settled confirmation did not send their
  receipts. Receipts are now submitted after each settled return. An automatic
  repair job resumes legacy PENDING fee payments linked to SETTLED returns; it uses
  the already-reserved cash and queues a receipt once. It neither pays unsettled
  returns nor marks a submitted payment PAID before confirmation.
- LendDesk's daily register displayed the original accrual status even when its
  linked fee or rebate settlement was PAID. The primary status now follows the
  linked settlement; the original value remains available as `accrualStatus`.
  Legacy single-day rebates without period fields link only to their accrual date.

Both Fees & Rebates pages refresh every ten seconds while visible and on window
focus. Existing filters, paging and record identities are preserved. BorrowDesk
uses the same legacy rebate-date fallback when deriving daily-row statuses.

## Existing records and deployment

Borrower Flyway V19 repairs stale fee statuses only when a same-organization FEE
payment is CONFIRMED for that exact bill and amount. It also repairs a rebate
status only when its exact organization, loan, rebate and amount match a stored
rebate payment. These updates do not move cash, create payments or infer payment
from a CLOSED loan. Unconfirmed bills remain unpaid.

Restart borrower-service and lender-service, then rebuild/reload both frontends.
Keep Kafka and both FIX adapters running to complete any resumed fee receipts.
The borrower repair interval defaults to ten seconds and is configurable with
`borrower.return-fee-repair-poll-ms`; normal `borrower.jobs-enabled` controls apply.
No changes to the fee/rebate rates or settlement schedules are required.

## Verification

- Confirm fees after a partial return, full return and a final bill arriving after
  closure. Check PAID in the bill API and both registers after delivery/refresh.
- Repeat the acknowledgement and verify cash, blocked balances and receipt counts
  do not change again.
- Verify an already-settled legacy return resumes its reserved fee once, while an
  unsettled return is left pending.
- Apply the reconciliation SQL twice with confirmed and pending payments. Only
  confirmed matches should be repaired, with no cash or rebate-payment duplication.
- Confirm a rebate payment and verify its settlement and linked daily row show
  PAID. A closed loan with no confirmed payment must continue to show unpaid status.
