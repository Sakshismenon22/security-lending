package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record BorrowerConnectionRequest(
    @NotBlank @Size(max = 100) String externalId,
    @Positive long borrowerOrganizationId,
    @NotBlank @Size(max = 160) String name) {}
