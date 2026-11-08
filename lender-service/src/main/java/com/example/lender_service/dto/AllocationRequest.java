package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;

public record AllocationRequest(
    @NotNull @Pattern(regexp = "CASH|SECURITY") String assetType,
    Long securityId,
    @PositiveOrZero long quantity,
    @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 6) BigDecimal cashAmount,
    @Positive Long marginCallId) {}
