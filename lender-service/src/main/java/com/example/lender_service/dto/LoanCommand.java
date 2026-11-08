package com.example.lender_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;

public record LoanCommand(
    @Positive long reservationId,
    @Positive long quantity,
    @NotEmpty List<@Valid AllocationRequest> collateral) {}
