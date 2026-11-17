package com.example.borrower_service.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;

/** Cash amount or whole security units; omitted fields retain the legacy whole-lot request. */
public record CollateralReleaseRequest(
    @DecimalMin("0.000001") @Digits(integer=18, fraction=6) BigDecimal cashAmount,
    @Min(1) Long quantity) {}
