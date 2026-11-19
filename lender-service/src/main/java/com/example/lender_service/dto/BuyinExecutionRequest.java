package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record BuyinExecutionRequest(
  @NotBlank @Size(max = 120) String clientRequestId,
  @Positive long buyinRequestId,
  @Positive long quantity,
  @NotNull
  @DecimalMin(value = "0", inclusive = false)
  @Digits(integer = 18, fraction = 6)
  BigDecimal price,
  @NotNull
  @DecimalMin("0")
  @Digits(integer = 18, fraction = 6)
  BigDecimal costs,
  @NotBlank @Size(max = 120) String executionReference
) {}
