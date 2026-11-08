package com.example.borrower_service.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;

/** Internal confirmation/projection contract, consumed from Kafka rather than exposed as a borrower API. */
public record BorrowerEvent(@NotBlank @Size(max=120) String eventId,@NotBlank String type,
                            @Positive long organizationId,@Positive long referenceId,
                            @Positive long version,@NotNull Instant occurredAt,@NotNull Map<String,Object> data) {}
