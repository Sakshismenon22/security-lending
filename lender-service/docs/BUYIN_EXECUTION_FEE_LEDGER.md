# Buy-in execution fees in both ledgers

Buy-in execution fees are already part of quantity × execution price + costs.
They must be visible without being added to the purchase or charged twice.

## LendDesk

Both the securities receipt's `BUY_IN_PURCHASE_PAYABLE` journal and the closure's
`BUY_IN_PAYABLE_SETTLED` journal carry `executionFeeAmount`. The existing signed
`amount` remains the full purchase/settlement total. The field is exposed by the
organization ledger API and the paged settlement-ledger overview.

The register shows **Execution fee (included)**, and expanded details state that
it is part of that entry's amount. This component column is not another posting;
the payable and its settlement continue to balance exactly as before.

## BorrowDesk

When a confirmed `BUY_IN_CLOSEOUT_REPORTED` summary is applied, a positive execution
fee creates one `BUY_IN_EXECUTION_FEE` allocation in the Funding ledger. It carries
the borrower-local loan ID, closeout reference and exact `executionFeeAmount`.
Its available-cash `amount` is zero: the purchase, including this fee, was funded
from collateral already pledged and applied. The allocation does not change cash,
pledged balances, or debit/credit totals. Available cash is recorded unchanged.

The Funding register shows **Execution fee included (₹)** and **Paid from collateral**.
The allocation resolves its lender and security from the linked loan. Statement
CSV exports include `loan_id` and `execution_fee_amount` so the component is visible
in downloaded records too. Zero-fee closures need no separate borrower allocation.
Duplicate delivery of a closure summary cannot create another allocation.

## Existing records

Restart both business services to apply their respective Flyway V20 migrations,
then rebuild/reload both desks. Kafka and the FIX adapters must be running for new
borrower closure summaries to arrive.

Lender V20 backfills the fee component from matching receipt/closeout records;
references are matched within the same organization and loan. Borrower V20 adds
missing fee allocations from existing confirmed closure summaries, skipping an
allocation with the same organization, loan, type and reference. Backfilled
borrower rows use migration time and the then-current available balance; they do
not invent a historical running cash balance. Neither migration moves money.

Tests cover both ledger APIs, preserved totals, repeated summary delivery,
repeated backfills, borrower cash-neutral allocations, CSV fields and visible
execution-fee amounts in both frontends.
