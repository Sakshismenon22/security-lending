package com.example.borrower_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public record LoanRequest(@Positive long reservationId,@Positive long quantity,@NotEmpty List<@Valid AllocationRequest> collateral) {}
