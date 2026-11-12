package com.example.lender_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Borrower's instruction to apply an already-blocked fee payment to an owner-issued bill. */
public record FeePaymentCommand(
    @Positive long loanId,
    @NotNull @DecimalMin(value = "0.000001") BigDecimal amount,
    @Positive long borrowerPaymentRequestId) {}
