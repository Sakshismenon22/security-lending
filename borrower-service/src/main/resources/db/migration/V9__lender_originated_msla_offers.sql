ALTER TABLE msla ADD COLUMN lender_msla_id BIGINT;
ALTER TABLE msla ADD CONSTRAINT uq_msla_lender_offer UNIQUE (lender_id, lender_msla_id);
