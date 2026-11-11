-- FEE_PAYMENT_RECEIVED was originally treated as an ordinary borrower event and
-- published to borrower.events.v1.  The borrower FIX adapter consumes only
-- borrower.commands.v1, so these settlement instructions never reached the lender.
-- Resetting their outbox publication flag lets the corrected routing republish the
-- same event ID.  The FIX and lender inboxes are idempotent by event ID.
UPDATE outbox_event
SET published = FALSE
WHERE event_type = 'FEE_PAYMENT_RECEIVED'
  AND published = TRUE;
