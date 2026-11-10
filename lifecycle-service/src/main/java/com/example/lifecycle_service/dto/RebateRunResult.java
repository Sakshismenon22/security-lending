package com.example.lifecycle_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RebateRunResult(
    long loanId, LocalDate processedThrough, int daysRecorded, int settlementsQueued,
    BigDecimal queuedSettlementAmount) {}
