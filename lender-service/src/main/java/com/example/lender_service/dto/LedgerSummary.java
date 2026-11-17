package com.example.lender_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Loan identifiers are lender-local; borrowerLoanId is the counterparty's reference. */
public record LedgerSummary(Long id, Long organizationId, String type, BigDecimal amount,
    String reference, Instant createdAt, Long loanId, Long connectionId,
    String borrowerName, Long borrowerLoanId) {}
