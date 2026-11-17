ALTER TABLE outbox_event ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'PENDING';
UPDATE outbox_event SET status='PUBLISHED' WHERE published=true;
CREATE INDEX ix_outbox_status_org ON outbox_event(organization_id,status);
