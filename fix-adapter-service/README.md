# FIX adapter service

A Spring Boot 4.1.2 / Java 17 transport bridge between Kafka and QuickFIX/J 2.3.2. One codebase runs as a **borrower initiator** or **lender acceptor**. It contains no lending approvals, inventory logic, fee/rebate calculations, collateral checks, recalls, deadlines, defaults, or buy-in execution.

```text
borrower-service
  → borrower.commands.v1
  → borrower adapter → FIX U1 → lender adapter
  → lender.commands.v1
  → lender-service

lender-service / lifecycle-service
  → lender.confirmations.v1
  → lender adapter → FIX U2 → borrower adapter
  → borrower.confirmations.v1
  → borrower-service
```

`PAYMENTS` commands in the borrower command topic are intentionally skipped by this adapter. A separate payment consumer group handles them. The adapter never invents a successful business response.

## Package structure

```text
com.example.fix_adapter_service
  config/       Validated configuration, profiles, Kafka settings, transport workers
  controller/   Adapter status and operational retry endpoints
  model/        One JPA entity or transport enum per file
  repository/   Typed Spring Data repositories and locking queries
  dto/          One envelope, routing, source-record, or status DTO per file
  response/     Response envelope, stable pagination, problem responses
  service/      Validation, routing, queues, codec, FIX engine, receipts, delivery, publication
```

Persistence entities are `AdapterIdentity`, `OutboundMessage`, `InboundMessage`, and `KafkaPublication`. Flyway owns the schema; Hibernate validates it. No borrower/lender business tables are copied into the adapter database.

## Run locally

Create two PostgreSQL databases:

```sql
CREATE DATABASE borrower_fix_db;
CREATE DATABASE lender_fix_db;
```

Start Kafka and create these topics, with at least one partition:

```text
borrower.commands.v1
lender.commands.v1
lender.confirmations.v1
borrower.confirmations.v1
borrower.commands.v1.DLT
lender.confirmations.v1.DLT
```

If increasing partitions, give each DLT at least as many partitions as its source topic. Business producers must use **borrower organization ID as the Kafka key** and stable event IDs across retries.

All local settings are in `application.properties` and the two `application-borrower.properties` / `application-lender.properties` files. Credentials are postgres/postgres, with Kafka enabled at localhost:9092. No environment variables are required. Start the lender adapter first, then the borrower adapter, in separate terminals from this folder:

```powershell
.\run-lender.cmd
```

```powershell
.\run-borrower.cmd
```

| Setting | Borrower | Lender |
|---|---|---|
| HTTP port | 8082 | 8083 |
| Database | borrower_fix_db | lender_fix_db |
| FIX role | Initiator | Acceptor |
| FIX CompID | BORROWER_DEMO | LENDER_DEMO |
| FIX connection | Connect to 127.0.0.1:9876 | Listen on 127.0.0.1:9876 |
| Kafka input | borrower.commands.v1 | lender.confirmations.v1 |
| Kafka output for received business messages | borrower.confirmations.v1 | lender.commands.v1 |
| FIX message store | runtime/fix/borrower | runtime/fix/lender |

The borrower profile is the default when no profile is supplied. Each adapter database binds to its side, institution ID, and sender CompID on first startup; using a database belonging to another identity fails startup. Keep FIX message-store directories separate and persistent across restarts. Automatic sequence resets are disabled.

Common environment overrides:

| Variable | Purpose |
|---|---|
| DB_URL, DB_USERNAME, DB_PASSWORD | Database connection for this deployment |
| KAFKA_BOOTSTRAP_SERVERS | Local-side Kafka brokers |
| HTTP_PORT, HTTP_BIND_ADDRESS | Operations HTTP server; binds to loopback by default |
| FIX_COMP_ID, INSTITUTION_ID | This deployment's FIX identity and business institution ID |
| COUNTERPARTY_ID, COUNTERPARTY_FIX_COMP_ID | External routing ID and remote FIX CompID |
| BORROWER_ORGANIZATION_IDS | Comma-separated allowed borrower IDs, default `1` |
| FIX_HOST, FIX_PORT | Initiator destination / acceptor port |
| FIX_LISTEN_ADDRESS | Acceptor bind address, default loopback |
| FIX_STORE_DIRECTORY | Persistent local QuickFIX/J file store |
| FIX_ENABLED, KAFKA_ENABLED | Disable either transport for standalone inspection |

Configure organization IDs to match the borrower database on **both** adapters. Configure the borrower's `LenderReference.externalId` to match the lender adapter's `INSTITUTION_ID`. Multiple counterparties can be configured with additional `adapter.routes[n]` entries; route external IDs and target CompIDs must be unique. All acceptor sessions in a deployment share the configured acceptor port.

Different local Kafka clusters are supported: configure each deployment's bootstrap servers independently. FIX carries the message between them. The embedded integration test uses one broker with separate topics for convenience.

## Message contracts

Borrower commands retain the existing borrower-service envelope, including `destination=COUNTERPARTY`, `mslaId`, and `lenderExternalId`. Lender confirmations retain the borrower's required `eventId`, `type`, `organizationId`, `referenceId`, `version`, `occurredAt`, and `data`, plus **local routing field `borrowerExternalId`**. That routing field is removed before sending the business payload to the borrower.

See [protocol and examples](docs/PROTOCOL.md) and the existing [borrower contracts](../borrower-service/docs/EVENT_CONTRACTS.md).

The wire format is an explicit **bilateral FIX 4.4 extension**, not a claim that U1/U2/U3 are standard securities-lending messages. `FIX44-SecuritiesLending.xml` extends the QuickFIX/J FIX44 dictionary and validates required custom fields. JSON is canonicalized and transported as Base64 UTF-8 to preserve Unicode and prevent FIX delimiters inside business data. Amounts are not converted through floating-point arithmetic.

## Delivery guarantees

1. Kafka input is acknowledged only after the outbound request has been durably queued. Invalid envelopes go to the source topic's DLT; database failures retry before dead-lettering.
2. The sender transmits the oldest unacknowledged event for each FIX session. It does not treat `sendToTarget=true` as durable delivery.
3. The receiving adapter commits both its inbound record and Kafka publication outbox before returning a **U3 durable transport receipt**.
4. Sender verifies receipt event ID, payload hash, and session before marking delivery acknowledged.
5. Receiver publishes its outbox to the appropriate local Kafka topic and marks it published only after a broker acknowledgment.

Delivery is **at least once**, not distributed exactly once. Event IDs deduplicate retries; conflicting content or routes under the same event ID are rejected. A database/network crash can replay a Kafka publication, so borrower/lender business consumers must also deduplicate. The borrower backend already has an inbox.

A U3 receipt means the peer adapter has durably stored the message and publication intent. It does **not** mean the lender approved a request, a loan settled, Kafka publication completed, or a business consumer processed it. U2 business confirmations are separate.

Disconnected sessions do not consume wire-attempt budgets. Missing receipts are retried with the same event ID. Defaults are 20 delivery attempts and 20 Kafka publication attempts; exhaustion suspends the item rather than dropping it. A suspended head blocks later messages for that session or organization to preserve ordering. Fix the underlying issue, then use the retry endpoint. Attempt counts reset for the new manual retry cycle.

## Operations API

All routes are under `/api/v1/adapter`:

| Method | Route | Purpose |
|---|---|---|
| GET | /status | Side, enabled transports, session state/sequence numbers, backlog counts |
| GET | /outbound | Outbound metadata and delivery status |
| GET | /inbound | Persisted received-message metadata |
| GET | /publications | Kafka publication status/errors |
| POST | /outbound/{id}/retry | Resume an unacknowledged/suspended delivery |
| POST | /publications/{id}/retry | Resume an unpublished/suspended Kafka publication |

Lists accept `page` (default 0) and `size` (default 50, max 200). Payloads are omitted from list serialization. Already acknowledged/published items cannot be resent through these endpoints. `/actuator/health` supplies application/database health; inspect `/status` for FIX connectivity.

There are no REST endpoints to submit loans, approve requests, manufacture confirmations, change sequences, or mark messages delivered manually. Business traffic originates from Kafka. Spring Security, FIX Logon authentication, and TLS are not configured; these profiles are a local demo setup. Persist the database and FIX file store together, run one active engine per CompID/session identity, and configure deployment access controls before external exposure.

## Tests and build

```powershell
mvn test
mvn package -DskipTests
```

The test suite covers envelope/routing validation, Unicode/decimal transport, dictionary validation, duplicate/conflicting messages, durable queues, receipts, ordered retries, broker failures, and operational endpoints. `LiveBridgeIntegrationTests` starts an embedded Kafka broker and **two real TCP FIX engines**, verifies both directions, then restarts the borrower adapter to verify persisted sequence numbers and duplicate suppression. It requires no external Kafka or business service. All resources are stopped after the test.

The adapter can be verified independently, but a business-level loan workflow still needs the lender and lifecycle services to consume commands and publish actual decisions.

Verified results: **20 tests passed in the complete Maven build**, including the real Kafka/FIX round trip and borrower-adapter restart. The **13 database/workflow tests also passed on an isolated PostgreSQL 18.6 database**. The temporary PostgreSQL server was stopped afterward; no application database was modified. The executable artifact is `target/fix-adapter-service-0.0.1-SNAPSHOT.jar`.

