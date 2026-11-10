ALTER TABLE msla ADD COLUMN rebate_settlement_frequency VARCHAR(20) DEFAULT 'AT_CLOSE' NOT NULL
 CHECK (rebate_settlement_frequency IN ('DAILY','WEEKLY','MONTHLY','AT_CLOSE'));

ALTER TABLE rebate ADD COLUMN period_start DATE;
ALTER TABLE rebate ADD COLUMN period_end DATE;
ALTER TABLE rebate ADD COLUMN settlement_frequency VARCHAR(20);
