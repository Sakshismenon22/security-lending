package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public record ReservationCommand(@Positive long locateId, @Positive long quantity) {}
