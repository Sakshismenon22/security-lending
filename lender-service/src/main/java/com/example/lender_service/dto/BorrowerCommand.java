package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import tools.jackson.databind.JsonNode;

public record BorrowerCommand(
    @NotBlank @Size(max = 120) String eventId,
    @NotBlank String type,
    @Positive long organizationId,
    @Positive long referenceId,
    @NotNull Instant occurredAt,
    @NotBlank String destination,
    @Positive long mslaId,
    @NotBlank String lenderExternalId,
    @NotNull JsonNode data) {}
