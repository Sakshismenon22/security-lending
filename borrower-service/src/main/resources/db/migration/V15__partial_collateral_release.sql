ALTER TABLE collateral_position ADD COLUMN requested_release_cash NUMERIC(24,6);
ALTER TABLE collateral_position ADD COLUMN requested_release_quantity BIGINT;
