package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record ReturnCommand(
    @Positive long loanId, @Positive long quantity, @PositiveOrZero long recallId) {}
