package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record SecurityRequest(
    @Positive long id,
    @NotBlank String symbol,
    @NotBlank String exchange,
    @NotBlank String name,
    String isin,
    @Pattern(regexp = "INR") @NotNull String currency) {}
