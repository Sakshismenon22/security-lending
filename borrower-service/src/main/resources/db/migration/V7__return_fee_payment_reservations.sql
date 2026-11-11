ALTER TABLE payment_request
    ADD COLUMN loan_return_id BIGINT REFERENCES loan_return(id);

CREATE INDEX ix_payment_request_return_pending
    ON payment_request(organization_id, loan_return_id, status);
