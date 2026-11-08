package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record OrganizationRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 100) String externalId,
    @Positive long publicReferenceId) {}
