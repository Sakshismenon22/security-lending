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
Default detection alone does not infer a return, inventory credit, collateral release or cash debit.
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

An external trade report does **not** confirm custody/cash settlement or loan close-out.
`collateralApplied` remains zero; the reported amount before settlement is the
quantity times execution price plus costs. The obligation stays open until its
actual return/collateral fulfillment or explicit loan closure is confirmed. Use the
Close loan after buy-in workflow below to perform that closure. The repository
has no external broker routing or collateral-liquidation integration; internal demo
orders and securities receipts are described below. Sample preview continues to use isolated local fixtures.

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

## Internal demo orders and inventory receipt

After the buy-in request is accepted, **Place buy-in order** submits:

`POST /api/v1/organizations/{org}/defaults/{id}/buy-in-orders`

```json
{
  "clientRequestId": "unique-order-reference",
  "buyinRequestId": 123,
  "type": "LIMIT",
  "quantity": 20,
  "limitPrice": 101.25,
  "costs": 5
}
```

This is the application's **internal demo matcher**, not an external broker or
exchange. MARKET orders omit `limitPrice`; LIMIT orders require a positive limit.
The quantity covers the complete outstanding default obligation. Matching uses a
fresh cached market quote, with maximum age 300 seconds by default. Limits match
only at or below their limit; unmatched/stale-quote orders remain PENDING. The
background matcher runs every five seconds and revalidates the default, parent
request and remaining loan quantity. Limits have no partial-fill simulation.

States are PENDING → EXECUTION_PENDING → FILLED. The matcher freezes the quote and
queues the existing lifecycle execution verification. Only the approved execution
report books the purchased inventory. Rejected or exhausted submissions expose
REJECTED/FAILED and a reason. Duplicate client IDs must describe identical orders;
concurrent orders/manual reports cannot reserve the same default twice.

- `POST /api/v1/organizations/{org}/buy-in-orders/{id}/match` checks an order now.
- `POST /api/v1/organizations/{org}/buy-in-orders/{id}/cancel` cancels an unmatched
  PENDING order. Filled or submitted-for-execution orders cannot be cancelled.

A confirmed **demo fill includes simulated receipt**. Purchased stock increases
`inventory_position.total_quantity` and therefore available quantity. Reserved and
loaned quantities stay unchanged: these are new purchased securities, not a return
of the borrower's original securities. For example, total 1,000 / loaned 100 /
available 900 becomes total 1,020 / loaned 100 / available 920 after a 20-unit buy-in.
The original loan's outstanding quantity, status, collateral, fees and rebates
remain unchanged. Loan close-out and collateral settlement require the explicit **Close loan after
buy-in** workflow described below.

External trade reports still use **Record execution**, then **Receive securities**
after custody receipt. The confirmation calls:

`POST /api/v1/organizations/{org}/buy-ins/{id}/inventory-receipts`

```json
{ "reference": "unique-custody-receipt-reference" }
```

Each trade can credit inventory once. Repeating the same receipt is idempotent;
changing its reference after receipt or reusing another trade's receipt reference
is rejected. Inventory and receipt metadata commit in one transaction, alongside:

- `BUY_IN_SECURITIES_RECEIVED` inventory history, with quantity and reference.
- A `BUY_IN_PURCHASE_PAYABLE` ledger entry for quantity × execution price + costs.
  This records a payable, not a cash debit or a deduction from held collateral.
- Audit/notification/outbox events for order placement, matching, cancellation,
  rejection/failure, fill and inventory booking. Event details preserve the order,
  price, quote timestamp, quantity, costs and execution reference.

The Defaults & Buy-ins screen shows saved orders, pending reasons, cancellations,
trade references and receipt status after refresh/reload. Its activity list includes
order and inventory receipt timestamps; inventory history and the settlement
ledger expose the same reference for reconciliation.

Restart lender-service to apply **V16** before using these features. Optional
settings are `BUYIN_ORDER_POLL_MS` (default 5000) and
`BUYIN_QUOTE_MAX_AGE_SECONDS` (default 300). A missing/stale quote never fills an
order. Market-data refresh must be operational for pending orders to progress.

## Close the loan after a buy-in

On the same **Defaults & Buy-ins** page, a case with received securities exposes
**Close loan after buy-in**. The dialog loads an authoritative eligibility preview:

- `GET /api/v1/organizations/{org}/defaults/{id}/loan-closeout-preview`
- `POST /api/v1/organizations/{org}/defaults/{id}/close-loan`
  with `{ "reference": "unique-closeout-reference" }`.

Closure covers the **entire remaining loan quantity**. The server rechecks tenant
ownership, all received buy-ins for that loan, pending returns/orders/obligation
requests, pending collateral pledges, and enough confirmed **cash collateral** to
pay the allocated purchase price plus costs. Security-only collateral or a cash
shortfall blocks closure; securities liquidation and external deficit payments
are not fabricated. Resolve those conditions before closing. Partial buy-ins may
be accumulated across cases, but a partial loan close-out is not offered here.

A successful close commits the following together under the organization lock:

1. Allocate received buy-in quantities to the remaining loan, FIFO by report ID.
   Costs are allocated proportionally if only part of a report is needed; unused
   purchased securities remain owned inventory with their remaining payable.
2. Write off the original on-loan securities claim by reducing both total and
   loaned inventory by the closed quantity. **Available inventory does not increase
   again**: it was credited when the purchased securities arrived. For example,
   after receipt, total 1,100 / loaned 100 / available 1,000 becomes total 1,000 /
   loaned 0 / available 1,000 on closure.
3. Apply cash collateral to the allocated buy-in cost, record the collateral
   movement and payable settlement, and release the remaining cash and eligible
   security collateral through the existing release-confirmation workflow.
4. Set outstanding quantity to zero and persist CLOSED/closed-at, the closeout
   quantity, costs, applied/returned cash and reference. Resolve loan default cases
   and cancel remaining recall/margin obligations rather than treating the buy-in
   as a physical borrower return.
5. Publish BorrowDesk confirmations and queue `LOAN_CLOSED` for lifecycle's existing
   final fee and rebate processing. Actual fee/rebate payment statuses update only
   when their authoritative confirmations arrive, according to the MSLA schedule.

BorrowDesk applies consumed cash to its pledged balance without refunding it, and
refunds only the collateral surplus. The loan closes with a timestamp. Pending
borrowed-security orders for the same security are cancelled; retained unsold
borrowed shares become owned shares, and any corresponding open short lots are
closed using the buy-in price. Failed/in-flight local return reservations are
released before applying the closeout so they cannot later reserve securities on
an already closed loan. Confirmation replay is idempotent.

There is one durable closeout per organization/loan. Repeating the same reference
returns it without another inventory/cash movement or billing command. A different
reference for an already closed loan is rejected. Received trade economics cannot
be amended without a reversal. The closed record is available from **View loan
closure** after reload, with audit and inventory/collateral histories preserving
the closure reference.

Restart the stack to apply lender migration **V17** and load the updated
borrower-service, lifecycle cash-history query, and **both FIX adapters** (the new
`COLLATERAL_APPLIED` confirmation is required). `start-lendora.cmd` can restart the
stack after existing processes are stopped. Leaving an old FIX adapter running
will prevent the collateral-application confirmation from reaching BorrowDesk.
