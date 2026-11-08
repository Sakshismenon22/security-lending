# Lender service

Spring Boot 4.1.2 / Java 17 lender backend, listening on **8084**, with PostgreSQL, JPA, Flyway, Kafka and STOMP WebSocket. Spring Security is intentionally excluded, matching the borrower backend.

## Responsibilities

The lender owns organizations/users, registered borrower connections, lendable inventory, agreement decisions, locate/reservation decisions, loan review, and recording confirmed collateral/securities settlements. It publishes confirmations compatible with the existing borrower service and FIX adapter.

The separate lifecycle service owns daily fees/rebates, EOD collateral checks, margin-call creation, deadline scheduling, defaults and buy-ins. This backend stores read projections of those reports. Recall, rebate-payment and buy-in buttons **submit requests to that owner**; they do not execute those workflows here.

```text
Borrower → borrower Kafka → borrower FIX adapter → FIX → lender FIX adapter
  → lender.commands.v1 → lender-service

lender-service → lender.confirmations.v1 → lender FIX adapter → FIX
  → borrower FIX adapter → borrower.confirmations.v1 → borrower-service

lender-service → lender.events.v1 / lender.lifecycle.commands.v1 → lifecycle-service
lifecycle-service → lender.lifecycle.events.v1 → lender-service → borrower confirmations
```

## Packages and models

```text
controller/  Lender frontend and operations endpoints
model/       One JPA entity per file; separate common projection interface
repository/  Typed Spring Data repositories; organization row locking
dto/         One validated request/command/event DTO per file
response/    API envelopes, pagination, problem responses
config/      CORS, WebSocket, Kafka, price/outbox workers
service/     Lender workflows, event intake/projections and publication
```

The updated draw.io diagram supplies the domain model: ORGANIZATION/USERS/ROLE, SECURITY_MASTER, INVENTORY_POSITION/HISTORY, MSLA, ASSETS/COLLATERAL_HAIRCUT, LOCATE_REQUEST, LOAN/LOAN_RETURN, COLLATERAL_POSITION/HISTORY, LENDER_ACCOUNT/LEDGER and externally owned obligation/billing views.

Organization IDs scope local records. Borrower connections contain both the remote institution ID and its remote organization ID. Each lending record keeps a **local ID and borrower resource ID separately**. No cross-database joins or borrower-business-table writes are used. Collateral securities reference `security_master`; cash collateral has a null security ID.

Extensions include reservation records, lifecycle requests, notifications/audit, inbox/outbox records and confirmation/source-version cursors. Financial amounts use BigDecimal / NUMERIC rather than DOUBLE. Accepted MSLAs are immutable in this implementation; amendments require a new contract workflow.

## Start

```sql
CREATE DATABASE lender_db;
```

Local settings are in `src/main/resources/application.properties` (postgres/postgres, Kafka localhost:9092 enabled). No environment variables are required.

```powershell
.\mvnw.cmd spring-boot:run
```

Kafka is enabled by default. Start the broker and create lending topics before starting this service. Confirmations use its durable outbox. Flyway creates the tables; Hibernate validates the schema. Swagger UI: `/swagger-ui.html`; OpenAPI: `/v3/api-docs`; health: `/actuator/health`.

The only background jobs are market-price refresh and outbox delivery. `lender.jobs-enabled=false` disables both. No local expiry, EOD risk, fee accrual, default or buy-in scheduler exists.

## Initial setup

Create a lender organization with a stable public reference ID, then its ADMIN user:

```http
POST /api/v1/organizations
Content-Type: application/json

{"name":"Demo Mutual Fund","externalId":"LENDER_DEMO","publicReferenceId":1000001}
```

`externalId` must match the lender adapter's `INSTITUTION_ID`. `publicReferenceId` is the ID published into borrower security-lending counterparty directories; coordinate it across institutions rather than assuming every lender's local database ID is globally unique.

Register a borrower connection under the returned **local lender organization ID**:

```http
POST /api/v1/organizations/1/borrowers
Content-Type: application/json

{"externalId":"BORROWER_DEMO","borrowerOrganizationId":1,"name":"Demo Borrower"}
```

This queues `LENDER_REFERENCE_UPDATED` through the adapter. The borrower backend then has the lender reference needed to propose an MSLA. Configure the same remote borrower ID in both adapters' organization allowlists. Unknown or inactive borrower connections cannot submit commands.

Import security references from the existing market-data service:

```http
POST /api/v1/securities/import
```

Use IDs returned by `GET /api/v1/securities` when adding inventory. The same market-data IDs are used across borrower and lender. Charts proxy the market-data history endpoint; cached prices use the latest completed minute. The lender never fetches Yahoo data separately.

## Lender workflow

1. Add lendable inventory; only unencumbered inventory can be withdrawn.
2. Receive an MSLA proposal through Kafka/FIX and approve/reject it.
3. Review locates. Approval reports availability but **does not reserve stock**.
4. Approve a separate borrower reservation request. This atomically increases reserved inventory and asks lifecycle to register its deadline.
5. Receive collateral intents and a loan request. Pledge intents can arrive before their loan request; they are staged and attached by borrower loan ID.
6. Record actual collateral receipt with a settlement reference. Pending collateral is not treated as received cash.
7. Approve the loan. Inventory remains reserved until actual securities settlement is recorded.
8. Record loan settlement. Confirmed initial collateral and the MSLA credit limit are checked; stock moves from reserved to loaned.
9. Record partial/full returns. Loaned inventory decreases only after return settlement. Recall fulfillment is reported separately by lifecycle.
10. Decide borrower collateral-release requests. Active-loan releases cannot leave insufficient confirmed collateral. Full closure does not automatically release collateral or calculate closing charges.

Settlement endpoints **record an externally confirmed transfer**; they do not connect to a custodian or payment rail. Use real custody/payment confirmation references, or clearly designated demo references. Repeating a settlement with its original reference is idempotent; conflicting references are rejected.

Initial collateral previews/checks support request review and settlement eligibility. They are not EOD valuation reports and never create margin calls. The authoritative daily results come from lifecycle.

## API

Organization-scoped prefix: `/api/v1/organizations/{org}`. `{org}` always means the local lender organization ID.

| Area | Endpoints |
|---|---|
| Organizations/users | Global `POST/GET /api/v1/organizations`, `GET /api/v1/organizations/{org}`, `POST/GET /users`, `PATCH /users/{id}`, global `GET /api/v1/organizations/roles` |
| Borrowers | `POST/GET /borrowers` |
| Dashboard/account | `GET /dashboard`, `GET /account`, `GET /ledger` |
| Inventory | `POST/GET /inventory`, `POST /inventory/{id}/adjustments`, `GET /inventory-history` |
| Agreements | `GET /agreements`, `GET /agreements/{id}`, `POST /agreements/{id}/approve`, `POST /agreements/{id}/reject` |
| Locates | `GET /locates`, `POST /locates/{id}/approve`, `POST /locates/{id}/reject` |
| Reservations | `GET /reservations`, `POST /reservations/{id}/approve`, `POST /reservations/{id}/reject` |
| Loans | `GET /loans`, `GET /loans/{id}`, `GET /loans/{id}/collateral-preview`, `POST /loans/{id}/approve`, `POST /loans/{id}/reject`, `POST /loans/{id}/settle` |
| Collateral | `GET /collateral`, `GET /collateral-history`, `POST /collateral/{id}/confirm`, `POST /collateral/{id}/reject`, `POST /collateral/{id}/release-confirmations`, `POST /collateral/{id}/release-rejections` |
| Returns | `GET /returns`, `POST /returns/{id}/settle`, `POST /returns/{id}/reject` |
| Owner read views | `GET /valuations`, `/margin-calls`, `/recalls`, `/fees`, `/rebates`, `/accruals`, `/defaults`, `/buy-ins`; add `/{id}` for view detail plus original reported data |
| Owner requests | `POST /loans/{id}/recall-requests`, `POST /rebates/{id}/payment-requests`, `POST /defaults/{id}/buy-in-requests`, `GET /lifecycle-requests` |
| Payments | `GET /rebate-payments`; actual payment confirmation comes from its owner |
| Notifications/audit | `GET /notifications`, `POST /notifications/{id}/read`, `GET /audit` |
| Market data | Global `GET /api/v1/securities`, `GET /api/v1/securities/{id}`, `GET /api/v1/securities/{id}/history`, `POST /api/v1/securities/import`, `POST /api/v1/securities/{id}/refresh` |

Paged lists accept `page` (default 0) and `size` (default 50, max 200). Responses use `{success,data,timestamp}`; page data uses `{content,page,size,totalElements,totalPages}`. Validation errors return 400, missing/cross-organization records 404, and invalid workflow transitions 409.

Example request bodies:

```json
{"securityId":1,"quantity":10000,"reference":"CUSTODY-INITIAL-001"}
```

```json
{"quantity":100,"reason":"Approved availability"}
```

```json
{"reference":"CONFIRMED-CUSTODY-TRANSFER-001"}
```

```json
{"quantity":50,"reason":"Portfolio sale","clientRequestId":"recall-request-unique-id"}
```

Reject actions use `{"reason":"..."}`. Rebate-payment and buy-in requests use `{"clientRequestId":"..."}`. These owner requests are idempotent by organization/client request ID and remain pending until the lifecycle owner responds. Recall permission and uncommitted quantities are validated before delegation.

## Integration and operational limits

Read [event contracts](docs/EVENT_CONTRACTS.md) before connecting the lifecycle service. The FIX adapter must attach `x-counterparty-id` from its configured session route; it is not accepted from a borrower HTTP request. Rebuild/restart the updated adapter when connecting this service.

Kafka delivery is at least once. Inbox deduplication, per-resource confirmation versions, transactional outbox records and organization row locks protect replay and inventory/cash transitions. Business consumers must retain their own inboxes. Outgoing confirmations preserve borrower resource IDs and remote organization IDs; they do not substitute local lender IDs.

STOMP endpoint: `/ws`; organization notifications: `/topic/organizations/{localOrg}/events`. Re-fetch REST views after reconnecting. The default CORS origin is `http://localhost:5174`.

Organization scoping is not authorization. Authentication and role enforcement remain deliberately excluded. Money is INR only; no FX, corporate-action processing, automated custody execution or MSLA amendment workflow is implemented.

Default/buy-in reports are read views. They do not themselves free loaned inventory or liquidate collateral. Recovery needs explicit settlement/account/position events from the owning workflow; the service refuses to infer those movements from a reported execution price. Bill/accrual corrections and that recovery settlement contract remain integration extensions for the lifecycle/custody implementation.

## Verification

```powershell
mvn test
mvn package -DskipTests
```

Tests apply Flyway migrations and validate JPA mappings; cover organization scoping, user administration, proposal replay, inventory encumbrances, reservations, staged collateral, review versus settlement, partial/full returns, owner-controlled expiry, recalls, externally reported rebates/margins and HTTP boundaries. The Kafka integration test uses an embedded broker to verify trusted command intake, published borrower-compatible confirmations and `.DLT` handling for missing counterparty metadata.

Verified: **21 lender tests passed in the final package build**; **19 database/workflow checks also passed against an isolated PostgreSQL 18.6 database**. The updated adapter passed its **20-test regression suite**, including real FIX/Kafka traffic, restart recovery, provenance headers and explicit dead-letter routing. All three executable JARs were rebuilt. The temporary PostgreSQL server was stopped; application databases were not modified.

