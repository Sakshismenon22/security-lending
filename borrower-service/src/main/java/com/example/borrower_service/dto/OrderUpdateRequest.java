package com.example.borrower_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Security, side and inventory source are immutable on an existing order. */
public record OrderUpdateRequest(
        @NotNull @Pattern(regexp = "MARKET|LIMIT") String type,
        @Positive long quantity,
        @DecimalMin("0.000001") @Digits(integer = 18, fraction = 6) BigDecimal limitPrice) {}
