package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record SettlementRequest(@NotBlank @Size(max = 160) String reference) {}
