# Borrower service

Spring Boot 4.1.2 / Java 17 backend for the borrower platform. Spring Security is intentionally excluded.

## Ownership

This service owns borrower organizations/users, accounts, simulated orders/trades/holdings, borrower requests, and the borrower-facing views of confirmed lending activity.

It contains **no FIX engine, lender approvals, lender inventory management, daily fee/rebate calculation, end-of-day risk engine, reservation/recall expiry scheduler, default management, or buy-in execution**. These responsibilities belong to the separate adapter, lender, and lifecycle services. See [service boundaries](docs/SERVICE_BOUNDARIES.md).

Fee, rebate, valuation, margin-call, recall, default, and buy-in tables in this database are **local read projections of externally issued events**, not authoritative engines. Their frontend endpoints are read-only, except for borrower responses (provide collateral, return securities, initiate fee payment).

## Packages

```text
com.example.borrower_service
  controller/  Borrower HTTP endpoints
  model/       One JPA entity per file; separate composite key classes
  repository/  Spring Data repositories and JPA transaction access
  dto/         One validated request/event DTO per file
  response/    API envelope, stable pagination, problem responses
  config/      HTTP/CORS, WebSocket, Kafka, borrower background jobs
  service/     Borrower workflows and confirmation projections
```

Flyway migrations are under `src/main/resources/db/migration`. Domain/entity mapping and service-specific extensions are documented in [ER mapping](docs/ER_MAPPING.md).

## Run

Create PostgreSQL database `borrower_db`. Local settings are in `src/main/resources/application.properties` (postgres/postgres, Kafka localhost:9092 enabled). No environment variables are required.

```powershell
.\mvnw.cmd spring-boot:run
```

The service listens on port **8081**. Swagger UI is `/swagger-ui.html`; OpenAPI is `/v3/api-docs`; health is `/actuator/health`.

Kafka is enabled by default for the local demo. Start the broker and create lending topics before starting this service. Commands use the durable outbox. Requests **stay pending until external confirmations arrive**; this backend does not simulate lender success.

The market-data service remains separate. `POST /api/v1/securities/import` copies its security master references. Prices are refreshed every minute via its history API, using only completed candles. Alternatively, publish completed quotes to `market.prices.v1`. Historical OHLCV for charts is proxied through the borrower history endpoint.

`borrower.jobs-enabled=false` disables borrower jobs. The only jobs are price refresh, simulated limit-order matching, and outbox publication. No business-obligation deadlines are processed here.

## API

Organization-scoped prefix: `/api/v1/organizations/{org}`.

| Area | Endpoints |
|---|---|
| Organizations | `POST/GET /api/v1/organizations`, `GET /api/v1/organizations/{org}` |
| Users | `GET/POST /users`, `PATCH /users/{id}`, `GET /api/v1/organizations/roles` |
| Dashboard | `GET /dashboard`, `GET /notifications`, `POST /notifications/{id}/read`, `GET /audit` |
| Reference data | `GET /lenders`; global `GET /api/v1/securities?search=INFY`, `GET /api/v1/securities/{id}` |
| Charts | `GET /api/v1/securities/{id}/history`, `POST /api/v1/securities/{id}/refresh` |
| Trading | `POST/GET /orders`, `POST /orders/{id}/cancel`, `GET /trades`, `GET /positions` |
| Portfolio | `GET /holdings`, `GET /account`, `GET /transactions`, `GET/PUT/DELETE /watchlist/{security}` (list uses `/watchlist`) |
| Funding | `POST /deposits`, `POST /withdrawals`, `GET /payments` |
| Agreements | `POST/GET /agreements`, `GET /agreements/{id}`, `GET /credit-history` |
| Locates | `POST/GET /locates`, `POST /locates/{id}/reserve` |
| Reservations | `GET /reservations`, `POST /reservations/{id}/release` |
| Loans | `POST/GET /loans`, `GET /loans/{id}`, `POST /loans/{id}/returns`, `GET /returns` |
| Collateral | `GET /collateral`, `GET /collateral/history`, `POST /loans/{id}/collateral`, `POST /collateral/{id}/release` |
| Valuation | `GET /loans/{id}/valuation` (externally reported), `GET /loans/{id}/collateral-preview` (indicative submission preview) |
| Margin calls | `GET /margin-calls`, `POST /margin-calls/{id}/collateral` |
| Recalls | `GET /recalls`, `POST /recalls/{id}/returns` |
| Billing | `GET /fees`, `POST /fees/{id}/payments`, `GET /rebates`, `GET /rebate-payments`, `GET /accruals`, `GET /statement.csv` |
| Exceptions | `GET /defaults`, `GET /buy-ins` — read-only external projections |

Paged resource lists accept `page` (default 0) and `size` (default 50, maximum 200). Responses use `{success,data,message,timestamp}`. Page data uses `{content,page,size,totalElements,totalPages}`. Request validation failures return HTTP 400 problem details; invalid business transitions return 409; cross-organization references return 404.

## Borrower workflow

1. Create an organization and its first `ADMIN` user. Additional users have ADMIN, TRADER, TREASURY, OPERATIONS, or READ_ONLY roles.
2. Import securities. Counterparty directory entries arrive through `LENDER_REFERENCE_UPDATED` events.
3. Initiate a deposit. Available cash increases only after `PAYMENT_CONFIRMED`.
4. Propose an MSLA. It remains `PROPOSED` until an external decision arrives.
5. Submit a locate. An approval does not itself reserve shares.
6. Submit a reservation against an approved locate. It remains `REQUESTED` until confirmed.
7. Request a loan against confirmed reserved quantity and allocate cash or owned, unpledged securities as collateral. Collateral is locally blocked while its pledge is pending.
8. Borrowed stock becomes sellable only after `LOAN_SETTLED`.
9. Respond to external margin calls with additional collateral and to recalls with return requests. These actions do not themselves mark an obligation fulfilled.
10. Return confirmations reduce the outstanding loan quantity. Collateral release requires its own confirmation. Closing a loan does not calculate fees or automatically return cash collateral.

### Example order

```json
{"securityId":1,"side":"BUY","type":"LIMIT","source":"OWNED","quantity":10,"limitPrice":1500}
```

`SELL / OWNED` sells owned, unpledged, unblocked shares. `SELL / BORROWED` sells settled borrowed shares. `BUY / BORROWED` is an explicit buy-to-cover instruction and cannot exceed the uncovered short position. Buying to cover and returning a loan are separate actions.

Trading is **paper execution against a delayed completed-minute close**. Market orders fill at the cached close; limit orders fill completely when that close satisfies their price. There is no exchange order book, partial-fill model, brokerage, tax, FX conversion, or live execution integration. FIFO short lots and weighted average long cost produce gross trading P&L.

### Example collateral allocation

```json
{"assetType":"SECURITY","securityId":2,"quantity":100,"cashAmount":0,"marginCallId":null}
```

Cash uses `assetType=CASH`, null `securityId`, zero quantity, and positive `cashAmount`. Haircut rules come from the MSLA. The borrower validates its proposed initial allocation and produces an **indicative** preview; the separate lifecycle service supplies authoritative valuations and margin decisions.

### Funding and fee payment

Funding body: `{"amount":100000,"clientRequestId":"unique-request-id"}`. A fee payment requires an `Idempotency-Key` header. Withdrawals and fee payments block funds until the payment-owning service confirms or rejects them. Reusing a key returns the original request; changed details with that key are rejected.

## Events and notifications

See [event contracts](docs/EVENT_CONTRACTS.md) for topics, request/confirmation examples, field definitions, versions, and responsibility boundaries.

WebSocket endpoint: `/ws`. Subscribe using STOMP to `/topic/organizations/{org}/events`. Treat WebSocket delivery as an invalidation signal and reload REST views after reconnecting; the outbox guarantees retryable Kafka publication, not delivery to disconnected browser sessions.

Organization IDs scope queries and writes, but **do not authorize access**. Authentication and role enforcement are deliberately pending, as requested. CORS permits the configured frontend origin, default `http://localhost:5173`.

## Verification

```powershell
mvn test
```

Tests use a separate H2 PostgreSQL-mode database, run all Flyway migrations, validate JPA mappings, and exercise borrower trading, funding confirmations, lending requests/settlement projections, collateral locks, margin/recall responses, duplicate/stale events, organization scoping, HTTP validation, pagination, and absence of lender-management endpoints. They do not connect to a live FIX adapter, lender, lifecycle service, or Kafka broker.

Verification on this implementation: **20 tests passed on H2 and on an isolated PostgreSQL 18.6 instance**. The temporary PostgreSQL instance was stopped after verification; the configured `borrower_db` was not modified.

