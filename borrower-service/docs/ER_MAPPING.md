# ER diagram mapping

The updated draw.io file is the **domain reference**, not a single database schema to copy into every microservice. The borrower schema follows its organization/multi-user ownership and the `COLLATERAL_POSITION.security_id` relationship. It adds operational states needed for asynchronous requests and keeps external-owner data as projections.

| ER area | Borrower implementation |
|---|---|
| ORGANIZATION, USERS, ROLE | `Organization`, `User`, `Role`. Multiple users belong to one organization. |
| TRADER_ACCOUNT | `TraderAccount`, one per organization, with available/order-or-payment-blocked/pledged cash buckets. |
| HOLDINGS, TRADE_HISTORY | `Holding`, `TradeHistory`; separate owned, borrowed, pledged, and blocked quantities. |
| SECURITY_MASTER | `SecurityMaster`, local reference cache using market-data IDs. OHLCV history stays in market-data service. |
| MSLA | `Msla`, borrower proposal and confirmed agreement view; loan terms are snapshotted at request time. |
| COLLATERAL_HAIRCUT, ASSETS | `CollateralHaircut`, `Asset`; eligibility and haircuts linked to MSLA and optional specific collateral security. |
| LOCATE_REQUEST | `LocateRequest`, request and externally reported decision. |
| LOAN, LOAN_RETURN | `Loan`, `LoanReturn`, with pending/confirmed settlement states and partial returns. |
| COLLATERAL_POSITION | `CollateralPosition`, with nullable security ID for cash and a security reference for securities collateral. |
| COLLATERAL_HISTORY | `CollateralHistory`, request and confirmation movements. |
| CREDIT_HISTORY | `CreditHistory`, borrower submission exposure trace. |
| MARGIN_RECALL, RECALL | `MarginCall`, `Recall`, read projections of external obligations; borrower responses link back to their IDs. |
| LENDING_FEE, REBATE, REBATE_PAYMENT | Read projections from calculation/payment owners. No local daily calculation. |
| DEFAULT, BUY_IN | `LoanDefault` (`loan_default`, avoiding a SQL keyword), `BuyIn`; read projections, no management/execution endpoints. |
| LENDER_ACCOUNT | Only `LenderReference` counterparties are cached. Lender balances and ledger belong to the lender backend. |
| INVENTORY_POSITION, INVENTORY_HISTORY | Omitted from borrower ownership; lender owns inventory. |
| BORROWER_REQUESTS | Outbound request responsibilities are represented by `LocateRequest`, `Reservation`, and `Loan`; no duplicate generic business-request table. |

Additional borrower tables:

- `reservation`: a specific locate's reservation request/confirmation, quantity, remaining quantity and expiry.
- `trade_order`, `short_lot`: paper order matching, locked quantities, and FIFO short-cover P&L.
- `payment_request`: idempotent borrower funding/fee-payment requests awaiting external confirmation.
- `fee_accrual_view`, `loan_valuation`: externally calculated daily accrual and valuation views.
- `watchlist_entry`, `notification`, `audit_event`: frontend support.
- `outbox_event`, `inbox_event`, `projection_cursor`: event delivery, deduplication, and ordering.

Columns are service-specific rather than a literal copy of every diagram label. For example, organization-scoped accounts use `organization_id`, holdings keep operational quantity buckets, and money/rates use PostgreSQL NUMERIC / Java BigDecimal rather than the diagram's DOUBLE. All entity files map their columns explicitly. Local FK constraints reference only local tables; references to externally owned records are identifiers/projections, not cross-database joins.

Core financial tables intentionally carry organization IDs for scoping even when that association can also be reached through a loan. Services verify that incoming/requested resources belong to the organization before changing state. Authentication is separate and intentionally not implemented yet.
