# Defaults and buy-ins

The lifecycle service owns deadline decisions. LendDesk reads lender-service's
persisted projections; it does not classify defaults in the browser.

## Automatic detection

Every **5 seconds**, lifecycle-service rechecks settled loans with outstanding
securities and open default cases. It acquires the same organization lock used by
lender loan/return/collateral workflows before reading the latest obligation.

- **Recall:** the recall deadline has passed and some recalled quantity remains
  unfulfilled. Only the remaining quantity, capped at loan outstanding, is due.
- **Maturity:** `maturityAt` has passed with securities still outstanding. A loan
  without a maturity date has no maturity default.
- **Margin call:** the deadline has passed and the required amount is not fully
  fulfilled. The case shows only the remaining amount.

The deadline must be strictly in the past. Pending settlement, fully fulfilled,
cancelled obligations and closed loans do not produce new cases. The durable
`lifecycle_default_case` table deduplicates by organization, obligation type,
obligation ID and deadline. Changes publish higher versions of `DEFAULT_REPORTED`;
cured cases publish `RESOLVED`. Repeated polling and restarts preserve the same
case ID. An extended deadline resolves the old case; a later missed deadline may
produce a new case.

These are obligation default cases, not automatic liquidation or loan close-out.
The loan remains operationally open so returns/collateral can cure the obligation.
No synthetic return, inventory credit, collateral release or cash debit is inferred.
Default reports generate lender notifications and are forwarded to BorrowDesk over
the existing FIX confirmation path. LendDesk refreshes its cases every 10 seconds.
With healthy services, a missed deadline is generally visible within about 15
seconds; broker/service outages can delay delivery. Cases are stored and their
reports use the existing durable outbox and failure notifications.

## Lender workflow and endpoints

1. Open **Defaults & Buy-ins** in live mode. Review the source obligation, deadline
   and remaining quantity/amount.
2. For an open **RECALL** or **MATURITY** case, click **Request buy-in**.
   `POST /api/v1/organizations/{org}/defaults/{id}/buy-in-requests`
   accepts `{ "clientRequestId": "unique-reference" }`.
3. Lifecycle consumes `BUY_IN_REQUESTED`, revalidates the obligation and publishes
   acceptance or rejection. Accepted requests wait for a real trade confirmation;
   no price or fill is fabricated. Margin cases require collateral fulfillment
   rather than a securities purchase and cannot request a buy-in.
4. Once accepted, click **Record execution**. Enter the execution price, costs and
   trade confirmation reference. This records an **already executed** full-quantity
   trade, not an exchange order. Partial execution reporting is not supported by
   this workflow. Use a new reference only for a new submission and preserve the
   client request ID when retrying the same submission.

   `POST /api/v1/organizations/{org}/defaults/{id}/buy-in-executions`

   ```json
   {
     "clientRequestId": "execution-unique-reference",
     "buyinRequestId": 123,
     "quantity": 20,
     "price": 101.25,
     "costs": 5,
     "executionReference": "TRADE-CONFIRMATION-456"
   }
   ```

5. The lender persists a `BUY_IN_EXECUTION_REQUESTED` request; lifecycle checks the
   accepted parent request, tenant/default/loan identity and current full outstanding
   obligation quantity. Stale quantities or cured obligations are rejected. A valid
   `BUY_IN_REPORTED` event completes both requests. The UI displays the execution
   reference, quantity, price, costs and status after refresh/reload.

Read model: `GET /api/v1/organizations/{org}/lenddesk/defaults-buyins`.
Every request endpoint enforces tenant ownership, duplicate pending requests and
stable client request IDs. Exhausted delivery attempts fail the corresponding
request and expose a reason; existing Failed deliveries Retry/Close actions apply.

A trade report does **not** confirm custody/cash settlement or loan close-out.
`collateralApplied` remains zero; the reported amount before settlement is the
quantity times execution price plus costs. The obligation stays open until its
actual return/collateral fulfillment or loan closure is confirmed. The repository
has no broker routing or collateral-liquidation integration; those require their
own settlement contracts. Sample preview continues to use isolated local fixtures.

## Run and test

Restart lender-service first to apply migration **V15**, then lifecycle-service and
LendDesk (or stop/restart the stack using `start-lendora.cmd`). Lifecycle and lender
must use the same `lender_db`; Kafka must be running.

`LIFECYCLE_DEFAULT_JOBS_ENABLED` defaults to `true` and
`LIFECYCLE_DEFAULT_POLL_MS` defaults to `5000`. Default detection can be disabled
independently of the other lifecycle jobs.

For a manual check, use a loan with a short future maturity, or issue a recall/margin
call with a short deadline. Leave part of the obligation unfulfilled, wait past its
deadline and inspect the case. Refresh/restart and verify the same case ID remains.
Fulfill the return/margin or close the loan through its normal workflow and verify
the case becomes Resolved. For a securities default, test Request buy-in, acceptance,
Record execution and refresh/reload. Try a mismatched/stale quantity using the API
and verify rejection without any cash or inventory movement.

Automated coverage includes lifecycle deadline/persistence tests, lender endpoint
and projection tests, and browser tests:

```sh
npm --prefix lendora run test:e2e -- defaultsBuyins
```
