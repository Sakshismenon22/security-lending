ALTER TABLE collateral_position ADD COLUMN margin_call_id BIGINT REFERENCES margin_recall(id);
ALTER TABLE loan_return ADD COLUMN recall_id BIGINT REFERENCES recall(id);
