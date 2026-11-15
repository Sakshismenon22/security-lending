package com.example.lender_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Enriched margin-call row used by the LendDesk live margin workflow. */
public record MarginCallSummary(
    Long id,
    Long ownerReferenceId,
    Long loanId,
    Long connectionId,
    String borrowerName,
    Long securityId,
    String securitySymbol,
    String securityName,
    BigDecimal amount,
    BigDecimal fulfilledAmount,
    BigDecimal remainingAmount,
    Instant dueAt,
    String reason,
    String status,
    Instant createdAt) {}
