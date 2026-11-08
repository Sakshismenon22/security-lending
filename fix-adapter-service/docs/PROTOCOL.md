# Bilateral adapter protocol v1

## FIX session

- FIX 4.4, borrower initiator, lender acceptor.
- Counterparty routes map external business IDs to CompIDs and allowed borrower organization IDs.
- Session logon, heartbeats, sequence numbers, resend, reconnect and file persistence are handled by QuickFIX/J.
- `UseDataDictionary=Y` with `FIX44-SecuritiesLending.xml`; unknown business fields are rejected.
- No automatic reset-on-logon, reset-on-disconnect, or reset-on-logout.

## Application messages

| MsgType(35) | Name | Meaning |
|---|---|---|
| U1 | SLCounterpartyCommand | Borrower command addressed to lender |
| U2 | SLBorrowerConfirmation | Lender/owner event addressed to borrower |
| U3 | SLDurableReceipt | Peer adapter has committed its inbound record and Kafka outbox |

| Tag | Dictionary field | Format |
|---|---|---|
| 9001 | SLTransportEventID | Stable application event ID, 1–120 ASCII letters/digits/`_.:-` |
| 9002 | SLTransportPayload | Base64 of canonical UTF-8 JSON; required for U1/U2, absent in U3 |
| 9003 | SLTransportPayloadHash | Lowercase SHA-256 hex of the canonical UTF-8 JSON |

The dictionary extends the official QuickFIX/J 2.3.2 FIX44 resource. These message types/tags require agreement by both counterparties. They are custom bilateral transport messages, not standard locate/loan FIX definitions. Standard FIX headers, BodyLength and CheckSum still apply independently of the application hash.

Maximum decoded JSON size defaults to 256 KiB and is configurable up to 1 MiB. Encoded and decoded sizes are checked. The application hash detects accidental/corrupt payload changes; it is not an authentication mechanism.

## Borrower command example

Publish to `borrower.commands.v1`, Kafka key `1`:

```json
{
  "eventId":"locate-request-uuid",
  "type":"LOCATE_REQUESTED",
  "organizationId":1,
  "referenceId":100,
  "occurredAt":"2026-11-08T12:00:00Z",
  "destination":"COUNTERPARTY",
  "mslaId":3,
  "lenderExternalId":"LENDER_DEMO",
  "data":{"agreementId":3,"securityId":1,"quantity":100,"expiresAt":"2026-11-09T12:00:00Z"}
}
```

Borrower adapter resolves LENDER_DEMO, validates organization permission, canonicalizes the payload, queues it, and sends U1. Lender adapter verifies the FIX session, direction, envelope ID/hash, allowed organization and local institution destination, persists it and its publication intent, then sends U3. The business envelope is published to `lender.commands.v1` with organization ID as key.

Supported counterparty commands:

```text
AGREEMENT_PROPOSED
LOCATE_REQUESTED
RESERVATION_REQUESTED
RESERVATION_RELEASE_REQUESTED
LOAN_REQUESTED
COLLATERAL_PLEDGE_REQUESTED
COLLATERAL_RELEASE_REQUESTED
RETURN_REQUESTED
```

DEPOSIT_REQUESTED, WITHDRAWAL_REQUESTED and FEE_REQUESTED with `destination=PAYMENTS` are skipped by the borrower adapter. A separate consumer group handles them. Other unknown commands/destinations fail validation.

## Lender confirmation example

Publish to `lender.confirmations.v1`, Kafka key `1`:

```json
{
  "eventId":"locate-approved-uuid",
  "type":"LOCATE_APPROVED",
  "organizationId":1,
  "referenceId":100,
  "version":1,
  "occurredAt":"2026-11-08T12:01:00Z",
  "borrowerExternalId":"BORROWER_DEMO",
  "data":{"approvedQuantity":100}
}
```

Lender adapter uses `borrowerExternalId` to resolve the outbound FIX session. It removes that local routing field from the wire/business envelope, sends U2, and waits for U3. Borrower adapter persists it and its publication intent, then publishes to `borrower.confirmations.v1`. The resulting JSON matches the borrower backend's `BorrowerEvent` DTO without adding a lender-management endpoint.

Supported confirmation types match the borrower backend contract: agreement and locate decisions; reservation confirmation/rejection/release/expiry/update; loan settlement/rejection/status; return settlement/rejection; collateral pledge/rejection/release; payment confirmation/rejection; lender references; holding imports; and reported fee, accrual, rebate, valuation, margin, recall, default and buy-in views.

The adapter checks the envelope, direction and route. It does **not** validate lending eligibility, calculate collateral, enforce recall rights, issue calls, assign resource versions, or decide whether a business response is correct. Those responsibilities remain with the owners.

## Correlation, ordering and replay

The receiving adapter also stamps Kafka headers `x-counterparty-id` and `x-fix-session` using its configured FIX route. The lender backend uses this provenance to distinguish borrower institutions that share the same numeric organization ID. These headers are adapter metadata, not fields supplied by the borrower UI.

- `referenceId` is preserved end to end. For an existing borrower request, the lender must echo the borrower's resource ID rather than substitute a lender database ID.
- `eventId` is stable across retransmissions. Same ID plus same content/session is a duplicate; the receiver reissues its receipt without creating a second publication.
- Reusing an event ID with changed content or routing is rejected. Canonical object-key ordering and precise JSON numbers make harmless key ordering differences deterministic.
- `version` on confirmations belongs to the business resource and must be assigned by the owner. The adapter does not invent versions.
- One unacknowledged message is allowed at the head of each configured FIX session's delivery queue. Kafka publications are serialized by organization partition key, including retries.
- U3 binds event ID, payload hash and configured session. A receipt on another session cannot acknowledge the original message.
- A missing receipt can cause a retransmission at a new FIX sequence number with the **same** event ID. Normal QuickFIX/J resend handling may also replay stored messages. Both cases are deduplicated at the application queue.
- A crash after broker acknowledgment but before marking a publication complete can duplicate Kafka output. Business consumers must retain their own inbox deduplication.
- A transport receipt is not a business decision. Wait for the appropriate business event to change borrower loan/reservation/account state.
