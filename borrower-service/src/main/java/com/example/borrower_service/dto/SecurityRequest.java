package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record SecurityRequest(@Positive long id,@NotBlank String symbol,@NotBlank String exchange,@NotBlank String name,String isin,@Pattern(regexp="INR") @NotNull String currency) {}
