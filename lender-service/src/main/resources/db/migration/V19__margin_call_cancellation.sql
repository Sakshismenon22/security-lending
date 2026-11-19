ALTER TABLE margin_recall ADD COLUMN cancellation_reference VARCHAR(160);
ALTER TABLE margin_recall ADD COLUMN cancellation_failure_reason VARCHAR(500);
ALTER TABLE margin_recall ADD COLUMN borrower_delivery_source_version BIGINT;
