ALTER TABLE loan_return ADD COLUMN failure_event_id VARCHAR(120);
ALTER TABLE loan_return ADD COLUMN failure_reason VARCHAR(1000);
ALTER TABLE loan_return ADD COLUMN assets_released BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE loan_return ADD COLUMN replacement_return_id BIGINT REFERENCES loan_return(id);
UPDATE loan_return SET assets_released=TRUE WHERE status='REJECTED';
