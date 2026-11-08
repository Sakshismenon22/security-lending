package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import tools.jackson.databind.JsonNode;

public record LifecycleEvent(
    @NotBlank @Size(max = 120) String eventId,
    @NotBlank String type,
    @Positive long organizationId,
    @Positive long referenceId,
    @Positive long version,
    @NotNull Instant occurredAt,
    @NotNull JsonNode data) {}
