package com.example.lender_service.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;

public record MarketPrice(BigDecimal value, Instant at) {}
