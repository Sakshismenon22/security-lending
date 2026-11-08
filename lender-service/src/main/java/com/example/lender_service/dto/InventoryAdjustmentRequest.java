package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record InventoryAdjustmentRequest(
    long quantityDelta, @NotBlank @Size(max = 160) String reference) {}
