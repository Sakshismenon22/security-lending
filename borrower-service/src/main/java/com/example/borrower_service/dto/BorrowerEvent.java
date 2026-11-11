package com.example.borrower_service.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Internal confirmation/projection contract, consumed from Kafka rather than exposed as a borrower API. */
public record BorrowerEvent(@NotBlank @Size(max=120) String eventId,@NotBlank String type,
                            @Positive long organizationId,@Positive long referenceId,
                            @Positive long version,@NotNull Instant occurredAt,@NotNull Map<String,Object> data,
                            JsonNode fixMsg) {
    public BorrowerEvent(String eventId,String type,long organizationId,long referenceId,long version,Instant occurredAt,Map<String,Object> data) {
        this(eventId,type,organizationId,referenceId,version,occurredAt,data,null);
    }
}
