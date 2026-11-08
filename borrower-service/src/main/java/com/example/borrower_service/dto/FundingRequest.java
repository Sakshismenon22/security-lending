package com.example.borrower_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record FundingRequest(@NotNull @DecimalMin("0.000001") @Digits(integer=18,fraction=6) BigDecimal amount,
                             @NotBlank @Size(max=120) String clientRequestId) {}
