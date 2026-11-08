package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record DecisionRequest(@Positive long quantity, @NotBlank @Size(max = 500) String reason) {}
