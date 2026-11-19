# Margin call delivery and cancellation

Automatic coverage checks previously saved `margin_recall` directly in the shared
lender database before publishing a same-version lifecycle event. Lender-service
then discarded the event as an already-applied projection, skipping borrower
forwarding. Direct Kafka publication also left a database/event failure window.

Lifecycle now queues a versioned owner instruction in the existing transactional
lender outbox. Lender-service alone applies the projection and queues the borrower
confirmation. Existing open calls retain their amount, fulfillment and deadline
when refreshed. Automatic checks do not overwrite a pending cancellation.

A lender repair job runs every five seconds by default
(`lender.margin-delivery-repair-poll-ms`). It queues a borrower snapshot for existing
open calls whose current source version has no recorded borrower delivery. The
marker and outbox record commit together, so repeated checks do not duplicate the
snapshot. This repairs the calls affected before the fix, including a new cycle
on a loan that already had a borrower confirmation in an earlier cycle. The
existing transport retries and Failed deliveries screen handle queued messages.

## Cancel before collateral submission

`POST /api/v1/organizations/{org}/margin-calls/{id}/cancel`

```json
{"reference":"cancel-margin-unique-reference"}
```

The endpoint is organization scoped. It accepts unfulfilled PENDING, OPEN or
OVERDUE calls with no pending or confirmed collateral response in the current
cycle. Repeating the request during cancellation or after cancellation is
idempotent. It records `CANCELLATION_PENDING` and queues a borrower verification
instruction using the same version stream as normal margin updates.

Borrower-service serializes verification with collateral submission under its
organization lock. It checks pending as well as confirmed collateral, including
requests still in transit to the lender. With no submitted response it marks the
call CANCELLED, preventing new pledges, and sends
`MARGIN_CALL_CANCELLATION_RESPONDED` through the existing borrower outbox/FIX path.
Otherwise it declines and leaves the call open. The lender validates the borrower,
loan and cancellation reference before applying the decision, publishing the
final status and recording audit/notifications. A delayed earlier-cycle owner
snapshot cannot reopen the cancelled call. New cycles carry their actual issue
time so earlier-cycle collateral is not mistaken for a new response.

LendDesk exposes **Cancel margin call** and an explicit confirmation dialog.
While borrower verification is pending, new margin requests remain blocked. Once
cancelled, a new request is allowed subject to the loan and shortfall guardrail.
Declined cancellation displays its reason; pending/posted collateral is settled
through the existing workflow. Cancellation does not release or reverse collateral
already submitted. Calls with a positive fulfilled amount cannot be cancelled.

Both margin-call screens refresh every ten seconds while visible; BorrowDesk also
refreshes on window focus. A CANCELLED borrower call disables response buttons.

## Deployment and verification

Restart lender-service, borrower-service, lifecycle-service and both FIX adapters,
and rebuild/reload the frontends. Lender Flyway V19 adds cancellation and delivery
tracking fields; keep Kafka and the FIX transport running for verification to
complete. If transport is down, the UI correctly remains pending until delivery
and the borrower decision complete.

1. Restart against a database with an undelivered automatic open call. Verify it
   appears on BorrowDesk after repair/transport and refresh without another scan.
2. Cancel an unfulfilled call. Verify both sides show CANCELLED and a new margin
   request can be submitted.
3. Submit borrower collateral and try cancellation before lender confirmation.
   Verify cancellation is unavailable or declined; the pledge remains intact.
4. Repeat with the pledge still in transit. Verify borrower verification declines
   cancellation and ordinary collateral confirmation can fulfill the call.
5. Retry the same cancel/decision and replay an older snapshot. Verify no duplicate
   cancellation and no reopened call.
