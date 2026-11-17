CREATE TABLE delivery_failure (
 id VARCHAR(180) PRIMARY KEY, organization_id BIGINT NOT NULL REFERENCES organization(id),
 outbox_id BIGINT NOT NULL REFERENCES outbox_event(id), event_id VARCHAR(120) NOT NULL,
 event_type VARCHAR(80) NOT NULL, reference_id VARCHAR(120) NOT NULL,
 status VARCHAR(30) NOT NULL, source VARCHAR(30) NOT NULL, stage VARCHAR(30) NOT NULL,
 transport_id BIGINT NOT NULL, attempts INTEGER NOT NULL, last_error VARCHAR(1000),
 updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_delivery_failure_org_status ON delivery_failure(organization_id,status);
