package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record LifecycleActionRequest(@NotBlank @Size(max = 120) String clientRequestId) {}
