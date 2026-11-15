package com.example.lender_service.dto;

import java.time.Instant;

/** Enriched recall row used by the LendDesk live recall workflow. */
public record RecallSummary(
    Long id,
    Long ownerReferenceId,
    Long loanId,
    Long connectionId,
    String borrowerName,
    Long securityId,
    String securitySymbol,
    String securityName,
    Long quantity,
    Long fulfilledQuantity,
    Long remainingQuantity,
    Long pendingReturnQuantity,
    Instant dueAt,
    String reason,
    String status,
    Instant createdAt) {}
