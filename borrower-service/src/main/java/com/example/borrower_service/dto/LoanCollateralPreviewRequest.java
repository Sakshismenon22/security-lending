package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record LoanCollateralPreviewRequest(
  @Positive long reservationId,
  @Positive long quantity,
  @NotNull @Size(max = 50) List<@Valid AllocationRequest> collateral
) {}
