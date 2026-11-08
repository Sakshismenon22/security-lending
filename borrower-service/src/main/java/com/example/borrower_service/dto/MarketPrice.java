package com.example.borrower_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketPrice(BigDecimal value,Instant at) {}
