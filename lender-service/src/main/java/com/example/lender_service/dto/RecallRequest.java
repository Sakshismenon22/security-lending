package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record RecallRequest(
    @Positive long quantity,
    @NotBlank @Size(max = 500) String reason,
    @NotBlank @Size(max = 120) String clientRequestId) {}
