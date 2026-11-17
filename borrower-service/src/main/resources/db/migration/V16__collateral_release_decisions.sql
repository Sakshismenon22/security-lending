ALTER TABLE collateral_position ADD COLUMN release_request_status VARCHAR(30);
ALTER TABLE collateral_position ADD COLUMN release_rejection_reason VARCHAR(1000);
UPDATE collateral_position SET release_request_status='PENDING' WHERE status='RELEASE_REQUESTED';
