package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BuyinOrderRequest(
  @NotBlank @Size(max = 120) String clientRequestId,
  @Positive long buyinRequestId,
  @NotBlank @Pattern(regexp = "MARKET|LIMIT") String type,
  @Positive long quantity,
  @DecimalMin(value = "0", inclusive = false)
  @Digits(integer = 18, fraction = 6)
  BigDecimal limitPrice,
  @NotNull @DecimalMin("0") @Digits(integer = 18, fraction = 6) BigDecimal costs
) {}
