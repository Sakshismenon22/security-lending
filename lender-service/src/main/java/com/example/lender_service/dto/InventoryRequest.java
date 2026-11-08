package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record InventoryRequest(
    @Positive long securityId,
    @Positive long quantity,
    @NotBlank @Size(max = 160) String reference) {}
