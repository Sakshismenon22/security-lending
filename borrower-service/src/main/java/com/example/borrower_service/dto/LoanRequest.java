package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record LoanRequest(@Positive long reservationId,@Positive long quantity,@NotEmpty @Size(max=50) List<@Valid AllocationRequest> collateral,
    @Future Instant maturityAt) {
  public LoanRequest(long reservationId, long quantity, List<AllocationRequest> collateral) {
    this(reservationId, quantity, collateral, null);
  }
}
