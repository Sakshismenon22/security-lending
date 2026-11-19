# Securities collateral sale before buy-in

The internal demo buy-in flow now supports:

1. Request and receive approval for the securities buy-in.
2. Select **Sell securities collateral**. Review all pledged security lots,
   quantities, latest cached prices and estimated proceeds, then confirm the sale.
3. Place the buy-in order as a separate action. Cash collateral must cover the
   requested purchase and execution fees. Prices are checked again when matching.
4. After execution/receipt, confirm the existing loan closeout. It applies the
   cash proceeds to the purchase and returns surplus collateral.

Sales are internal demo executions using the latest valid cached price, including
outside market hours. Quote age does not block the sale; quote price and original
timestamp are stored for provenance. No external exchange/broker order is sent.
There is no additional simulated broker charge for the collateral sale itself.
The existing buy-in execution fees continue to be included in the purchase cost.

## Backend and ownership

`GET /api/v1/organizations/{org}/defaults/{id}/collateral-sale-preview`

`POST /api/v1/organizations/{org}/defaults/{id}/sell-collateral`

```json
{"reference":"collateral-sale-client-request-unique-key","saleScope":"token-from-preview"}
```

The preview token fixes the reviewed lot identities, quantities and default version. If those change, confirmation is rejected for a new review; quote prices may change as disclosed. The reference is a stable client request key. Retrying the same organization,
default and key returns the original sale even if the loan has since closed.
Reusing it for another default is rejected. All conversions and outbox records
commit atomically under the lender organization lock.

The backend requires an open recall/maturity default on an outstanding loan and
an accepted/completed buy-in request for that exact default. Pending returns,
pledges and collateral releases must be resolved first. It sells all remaining
POSTED security collateral lots, using valid positive non-future quotes. Missing
quotes or an invalid lot roll back the entire sale. Each lot is converted to cash
collateral with zero haircut. The recorded history retains original security,
quantity, execution price and quote time. The lender cash-collateral balance and
cash opening movements are credited once, preserving subsequent rebate timing.

Sale proceeds remain pledged. They do not become borrower available cash until
surplus collateral is released. Backend order placement rejects unsold security
collateral or insufficient funding; matching also rechecks funding before a fill.
A sale does not itself place an order or close the loan. Cash-only loans continue
with the existing place-order flow.

## Borrower synchronization

`COLLATERAL_LIQUIDATED` is delivered over the normal durable confirmation/FIX path,
using the collateral position's existing version stream. Borrower-service checks
the source lot/security/quantity/proceeds, removes sold owned and pledged shares,
records realized P&L, converts the position to cash, and increases pledged cash.
Available cash is unchanged. Notifications, collateral history and a Funding
ledger allocation record expose the sale and held proceeds. Repeated delivery
cannot sell shares or credit proceeds again. Later collateral application and
surplus-release events use the same converted lot and ordered version stream.

## Deployment

Restart lender-service and borrower-service for their Flyway V21 migrations.
Restart both FIX adapters for the new confirmation type, keep Kafka running, and
rebuild/reload both frontends. Existing unsold security collateral will show the
sale step automatically. Existing pending demo orders cannot fill until their
collateral is sold and enough cash is available.

Tests cover security-sale to buy-in to loan-closeout, exact surplus, duplicate
requests/confirmations, missing quotes, tenant isolation, borrower holdings and
cash balances, FIX routing, the required UI sequence and insufficient proceeds.
