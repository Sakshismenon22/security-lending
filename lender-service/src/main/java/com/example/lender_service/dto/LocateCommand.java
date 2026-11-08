package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record LocateCommand(
    @Positive long agreementId,
    @Positive long securityId,
    @Positive long quantity,
    @NotNull Instant expiresAt) {}
