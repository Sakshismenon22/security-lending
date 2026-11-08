package com.example.borrower_service.dto;

import jakarta.validation.constraints.Positive;

public record ReservationRequest(@Positive long quantity) {}
