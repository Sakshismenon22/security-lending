package com.example.lender_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;

public record PledgeCommand(
    @Positive long positionId, @NotNull @Valid AllocationRequest allocation) {}
