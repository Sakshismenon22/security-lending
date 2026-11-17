CREATE TABLE return_cancellation (
 event_id VARCHAR(120) PRIMARY KEY,
 organization_id BIGINT NOT NULL REFERENCES organization(id),
 connection_id BIGINT NOT NULL REFERENCES borrower_connection(id),
 borrower_return_id BIGINT NOT NULL, confirmation TEXT NOT NULL,
 UNIQUE(connection_id,borrower_return_id)
);
