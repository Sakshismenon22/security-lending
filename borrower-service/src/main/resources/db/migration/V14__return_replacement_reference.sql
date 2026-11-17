ALTER TABLE delivery_failure ADD COLUMN replacement_reference_id BIGINT;
ALTER TABLE delivery_failure ADD COLUMN compensation_complete BOOLEAN NOT NULL DEFAULT FALSE;
