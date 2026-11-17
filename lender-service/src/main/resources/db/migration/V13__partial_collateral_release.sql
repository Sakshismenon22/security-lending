ALTER TABLE collateral_position ADD COLUMN requested_release_cash NUMERIC(24,6);
ALTER TABLE collateral_position ADD COLUMN requested_release_quantity BIGINT;
ALTER TABLE collateral_history ADD COLUMN cash_amount NUMERIC(24,6);
UPDATE collateral_history SET cash_amount = (SELECT p.cash_amount FROM collateral_position p WHERE p.id=collateral_history.position_id) WHERE type IN ('PLEDGE_CONFIRMED','RELEASE_CONFIRMED');
