package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record OrderRequest(@Positive long securityId,@NotNull @Pattern(regexp="BUY|SELL") String side,
        @NotNull @Pattern(regexp="MARKET|LIMIT") String type,@NotNull @Pattern(regexp="OWNED|BORROWED") String source,
        @Positive long quantity,@DecimalMin("0.000001") @Digits(integer=18,fraction=6) BigDecimal limitPrice) {}
