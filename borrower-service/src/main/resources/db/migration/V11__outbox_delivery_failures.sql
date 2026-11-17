ALTER TABLE outbox_event ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
ALTER TABLE outbox_event ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE outbox_event ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE outbox_event ADD COLUMN last_error VARCHAR(1000);
UPDATE outbox_event SET status='PUBLISHED' WHERE published=true;
CREATE INDEX ix_outbox_status_org ON outbox_event(organization_id,status);
