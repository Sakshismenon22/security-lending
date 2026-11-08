package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record QuoteRequest(@NotNull @DecimalMin("0.000001") @Digits(integer=18,fraction=6) BigDecimal price,@NotNull Instant priceAt) {}
