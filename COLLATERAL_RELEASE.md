# Borrower collateral release requests

BorrowDesk → Loans → Request collateral release (inline loan details or loan workspace).
Select Cash or Security, then a confirmed collateral lot. Enter an amount for cash or whole units for the selected security. Securities are identified by symbol and exchange. A zero-limit lot remains selectable so the form can explain why it cannot be released; submitting above the excess remains blocked.
The form shows required coverage, confirmed recognized collateral, amounts already pending release,
and the maximum releasable amount for each lot. Unconfirmed pledges never increase this limit.
Security limits include the position's haircut and cached market price, rounded down to whole units.
Pending requests reserve excess across all lots of the loan; they do not move cash or securities.

LendDesk (live mode) → Loans → Manage → Operation → Accept collateral release or Reject collateral release.
The requested amount and held balance are shown before review. Acceptance recalculates coverage
using current cached prices and the MSLA coverage ratio under the organization's transaction lock.
If prices have changed and coverage would be insufficient, acceptance fails and the request remains
pending for rejection or a subsequent check. Rejection clears the pending amount without moving assets.
Borrower migration V16 preserves a separate release decision and rejection reason while the collateral itself remains POSTED. Both loan views and the collateral screen check for changes every five seconds while visible (and on focus), refresh in the background, and show the rejection reason. A new request clears the old decision. The release form refreshes its limits while open. Older rejection envelopes with the reason in `reference` are also supported.

Acceptance reduces only the requested part of the lot and lender cash collateral held. The authoritative
confirmation credits only that cash amount or unpledges only those security units on BorrowDesk.
A partial lot returns to POSTED; a fully released lot becomes RELEASED. Versioned confirmations
protect against duplicate credits. Full loan closure releases the entire remaining lot even if a
partial release was pending. Defaulted and recovered loans retain their existing lifecycle ownership.
Cash movement amounts are stored in immutable collateral history so partial releases do not rewrite
prior rebate accrual balances.

## APIs

- Borrower `GET /api/v1/organizations/{org}/loans/{id}/collateral-release-preview`
  returns `availableExcess`, `pendingReleaseValue`, `requiredCollateral`, `recognizedValue`,
  and `positions` with `maxCashAmount` / `maxQuantity`.
- Borrower `POST /api/v1/organizations/{org}/collateral/{id}/release`
  accepts `{"cashAmount": 500}` for cash or `{"quantity": 3}` for securities.
  An omitted or empty body preserves the whole-lot request, subject to the same excess check.
  The position must belong to the borrower; loan must be ACTIVE, RETURN_PENDING, or CLOSED.
- Lender `POST /api/v1/organizations/{org}/collateral/{id}/release-confirmations`
  accepts `{"reference":"custody-reference"}` and rechecks coverage before committing.
- Lender `POST /api/v1/organizations/{org}/collateral/{id}/release-rejections`
  accepts `{"reason":"Keep coverage buffer"}`.

## Deployment and verification

Restart borrower-service (migrations V15 and V16) and lender-service (migration V13), then deploy the updated
lifecycle-service and both frontends. Lender migration V13 also backfills historical cash movement
amounts. Deploy the lifecycle update after that migration: it reads this new history column.

1. With loan value 10,000, 105% required coverage and cash collateral 11,000, request at most 500.
   A larger amount must fail on the borrower API as well as being blocked in the form.
2. Verify assets stay pledged until the lender accepts. Accept 400 from Manage Loan; both services
   should retain 10,600 collateral and the borrower should receive only 400.
3. Request another 100, then raise the loan security price so required coverage exceeds remaining
   collateral after release. Acceptance must fail without debiting collateral. Reject to clear it.
4. Try multiple lots: each pending request must reduce the excess available to other requests.
   Repeat with security collateral to check haircut-adjusted, whole-unit limits.
5. Fully return a loan while a partial release is pending; all remaining collateral must be released.

Regression cases are in BorrowerWorkflowTests and LenderWorkflowTests. The test demo includes the
release-limit preview endpoint and a partial cash request example.
