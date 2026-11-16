package com.example.lender_service.dto;

import jakarta.validation.constraints.NotNull;

public record MarginGuardrailRequest(@NotNull Boolean enabled) {}
