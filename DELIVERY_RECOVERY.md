# Automatic delivery failure recovery

Branch: `feature/automatic-request-failure-recovery`.

## What changes

Borrower/lender Kafka outboxes and both FIX adapters retry automatically. A disconnected FIX session now consumes attempts. When the configured budget is exhausted, delivery is persisted as `FAILED`, automatic attempts stop, and the original immutable event and payload are retained. Existing adapter `SUSPENDED` rows are migrated to `FAILED`.

The feature covers all event types using those outboxes and transports, including locates, reservations, loans, recalls, returns, margin calls, confirmations and lifecycle reports. It does not invent a successful business result when the broker accepts a message. An application-level validation failure or rejected command is still a business rejection, not a transport failure.

The `delivery_failure` table in each business database is the durable request-delivery status and UI notification record. Kafka source failures also set `outbox_event.status=FAILED`; FIX failures set `outbound_message.state=FAILED`; receiver Kafka failures set `kafka_publication.state=FAILED`. Business states such as ACTIVE/CLOSED/SETTLED are independent and are never overwritten by a transport outage.

Every 10 seconds the business services scan failed local outbox events and query both adapter HTTP APIs. Matching uses the original event ID in the originating local outbox; the peer's organization ID is never trusted for ownership. Notifications are persisted in the business `notification` table. The portals poll the business API, so notifications and recovery continue working without Kafka. LendDesk shows the panel in live mode. The failed-deliveries control is available on every page.

## Retry and close

- **Retry:** restarts the failed stage's attempt counter and reuses its exact event ID and payload. Receiver inbox and adapter deduplication protect against duplicate requests. Recovery status becomes RETRYING, then RECOVERED after source publication/peer acknowledgement/receiver publication succeeds. Downstream stages can fail independently and produce another alert for the same event.
- **Close delivery:** sets the exhausted transport and its delivery-failure record to CLOSED. It retains all records and allows later traffic in the same queue to proceed. Closed deliveries cannot be retried through the UI.
- Failed queue heads preserve ordering until the user retries or closes them. Closing a failed predecessor can leave later dependent requests invalid; inspect their current business state.

**Close is not a business cancellation for general messages.** A timeout can happen after Kafka or the peer persisted the request. The user must reconcile the current business state before submitting a replacement; existing financial reservations and duplicate-request checks remain enforced. Releasing cash, stock or reservation quantities merely because an acknowledgement timed out would risk double execution. Automatic cancellation of an already accepted financial request requires a separate domain cancellation/acknowledgement protocol and is deliberately not performed by transport recovery.

A late durable receipt changes an outbound failure to ACKNOWLEDGED, and the corresponding open notification is resolved on the next scan. A retry that succeeds is also resolved automatically. No timeout generates a new business event ID.

## API

Both business services expose organization-scoped endpoints:

- `GET /api/v1/organizations/{org}/delivery-failures` lists FAILED and RETRYING records.
- `POST /api/v1/organizations/{org}/delivery-failures/{id}/retry` retries an exhausted delivery.
- `POST /api/v1/organizations/{org}/delivery-failures/{id}/close` closes the delivery.

Failure IDs are strings, for example `OUTBOX:kafka:12`, `BORROWER_FIX:outbound:8`, or `LENDER_FIX:publications:9`. URL-encode the ID. A failure owned by another organization returns 404. Decisions require FAILED status; stale decisions return conflict. Adapter errors preserve the failure for reconciliation rather than assuming the decision succeeded.

The test-demo Operations section includes list/retry/close actions for both services. Its resource input accepts these failure keys.

Adapter operations include `GET /api/v1/adapter/failures`, individual outbound/publication reads, and `POST /api/v1/adapter/{outbound|publications}/{id}/{retry|close}`. Direct adapter operations retain their existing local operations access model. Adapter HTTP endpoints are bound to loopback by default. Business recovery URLs are deployment configuration, never supplied by a user request.

## Configuration and rollout

Restart both business services and both adapters to apply Flyway migrations. Do not reset or delete Kafka offsets, transport records, inbox records or financial request rows.

- `borrower.outbox-max-attempts=20`
- `borrower.outbox-retry-seconds=5`
- `lender.outbox-max-attempts=20` (five-second retry delay)
- `adapter.max-delivery-attempts=20`
- `adapter.max-publication-attempts=20`
- `adapter.retry-seconds=10`
- `adapter.receipt-timeout-seconds=30`
- `recovery.enabled=true`
- `recovery.poll-ms=10000`
- `recovery.borrower-adapter-url=http://localhost:8082`
- `recovery.lender-adapter-url=http://localhost:8083`

Both business services need HTTP access to both adapter URLs to identify failures at the originating sender and receiving Kafka stage. Adapter discovery is retried if an adapter is temporarily unavailable. Kafka/jobs intentionally disabled in configuration are not treated as an outage; re-enable their workers to resume processing. Failure history remains in the database after alerts are closed/recovered.

## Verification

Regression tests cover FIX disconnect exhaustion, Kafka receiver exhaustion, same-ID retry, closed-queue advancement, late acknowledgements, source Kafka failure persistence, organization isolation and invalid recovery transitions. Test-demo contracts cover recovery-key validation and URL encoding. Browser checks with mocked API responses cover failure notifications, retry, RETRYING status and close actions in both portals.

Frontend builds and Node contract/data tests pass. Java parsing succeeds. Backend test execution in this environment is blocked by DNS resolution of Maven Central when resolving the existing Spring Boot parent; run Maven tests in an environment with repository access before deployment.

## Failed loan returns: automatic asset restoration

Loan returns have a domain compensation workflow on this branch:

1. Exhausted delivery marks the actual borrower `loan_return.status` as `FAILED`. Source Kafka exhaustion updates it immediately; adapter exhaustion updates it in the recovery scan.
2. Borrower-service uses HTTP to ask lender-service to reconcile/cancel that exact return. This works while Kafka is stopped. The lender locks the same organization row used by command receipt and return settlement. A durable `return_cancellation` fence prevents a delayed original command from creating or settling the cancelled return.
3. If the return was never settled, the lender cancels any pending lender return and returns a versioned cancellation confirmation. The borrower applies its existing reservation-release accounting exactly once: restores the original owned/borrowed shares, retains their cost basis, refunds the return-linked blocked fee cash, and restores those fee bills to DUE. The original return remains FAILED with `assets_released=true`. The loan becomes ACTIVE when no other pending/unreconciled return remains. Posted loan collateral remains pledged because the loan is still outstanding.
4. If settlement won the race, the lender returns an authoritative settlement confirmation instead. The borrower applies settlement and does not restore the securities.
5. If lender-service is unavailable, the return is FAILED but `assets_released=false`. The UI explains that cancellation confirmation is pending. These shares still count against return/recall capacity, preventing a replacement from spending the same assets. The recovery scan keeps trying reconciliation.
6. After cancellation, old delivery stages are closed automatically. Retry creates a fresh return ID and event, reserves its assets again, and links `replacement_return_id` on the failed return. Repeating retry cannot create a second replacement. This is the deliberate exception to same-event transport retry: replaying a compensated return would settle assets already made available again. Other message types retain their original retry behavior.

Restart both borrower-service and lender-service to apply the new Flyway migrations. Configure `recovery.lender-service-url` (default `http://localhost:8084`). Set `recovery.borrower-counterparty-id` (default `BORROWER_DEMO`) to match the borrower FIX institution ID and the trusted external identifier in the lender connection. The endpoint uses the existing trusted `x-counterparty-id` transport convention and validates the original organization, lender, agreement, loan, return and quantity.

SQL to verify borrower restoration:

```sql
SELECT id, status, failure_event_id, assets_released, replacement_return_id
FROM loan_return ORDER BY id DESC;
```

Expected after a cancelled failed delivery: FAILED, assets_released=true, restored available shares/cash, unchanged loan outstanding quantity, and no delayed settlement of the old return.

## Valuation delivery failures

Exhausted valuation-command or valuation-result publication also marks its `lifecycle_request` as FAILED and stores `failure_reason`. The valuation-run status API returns that failure so the shared valuation button ends its pending state, displays the reason and allows another run. Polling continues until an authoritative terminal state; it no longer stops at an arbitrary one-minute frontend timeout before backend retries finish.

A new valuation run closes exhausted predecessor runs and their failed queue entries. Retrying the original delivery from notifications instead reopens that same failed run as PENDING. An authoritative result that arrives after a transport timeout may still complete the failed run and clear its transport error; it cannot reopen an explicitly CLOSED run. Restart lender-service to apply the new failure-reason column.
