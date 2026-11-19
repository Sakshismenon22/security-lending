# Multi-collateral loan requests

In BorrowDesk, open Reservations, select an active reservation, and choose
**Convert to loan**. Enter the loan quantity and maturity, then use **Add collateral**
to build a mix of cash and eligible securities. Each row can be changed or removed.

The allocation table displays available cash/units, quoted unit price, gross
market value, MSLA haircut and haircut-adjusted eligible value. The totals show
loan market value, required collateral, total market/eligible values, coverage,
shortfall and surplus. Calculations refresh after edits, every ten seconds while
visible and on window focus. Repeated rows for one asset share its available balance.

Security availability is owned quantity minus pledged and blocked-owned quantity.
Cash availability is spendable cash, excluding already blocked/pledged cash.
Unavailable quotes remain unknown; they are never represented as a zero price.
An unavailable quote for an unused eligible asset does not block a cash-only draft.

## Preview API

`POST /api/v1/organizations/{org}/loans/collateral-preview`

```json
{
  "reservationId": 8,
  "quantity": 10,
  "collateral": [
    {"assetType":"CASH","securityId":null,"quantity":0,"cashAmount":300},
    {"assetType":"SECURITY","securityId":3,"quantity":5,"cashAmount":0},
    {"assetType":"SECURITY","securityId":4,"quantity":2,"cashAmount":0}
  ]
}
```

The preview is organization scoped and read-only. It creates no loans, collateral,
reservations or cash movements. Empty lists are allowed for the initial preview;
up to 50 allocations are supported. It returns MSLA options, each row's current
valuation/errors, aggregate coverage and an `eligible` flag. It checks reservation
state/expiry/remaining quantity, MSLA validity/credit, permitted assets, prices,
haircuts and aggregate asset usage. Rows are valued using:

- Cash market value = cash amount.
- Security market value = quantity × current cached security price.
- Eligible value = market value × (1 − MSLA haircut), rounded to six decimals per lot.
- Required collateral = loan market value × MSLA coverage ratio.

The submit action continues to use the existing `POST /loans` contract, sending
all allocations and the maturity. That transaction locks the organization,
checks current eligibility/credit, reserves cash and securities for every lot,
and validates their combined coverage. The outgoing lender command preserves the
full collateral list; frontend prices or haircuts are never accepted as authority.
The preview is indicative and does not guarantee submission if prices or balances
change before the final transaction.

## Deployment and verification

Restart borrower-service and rebuild/reload BorrowDesk. No database migration,
lender-service deployment, or new service is required for this change.

Backend regressions verify mixed cash plus two security lots, exact gross and
eligible totals, read-only previews, all lots in the outgoing command, actual
pledging on submit, duplicate-asset over-allocation, missing quotes and tenant
isolation. Browser checks verify the live table, mixed submission, row removal,
insufficient coverage and mobile containment.
