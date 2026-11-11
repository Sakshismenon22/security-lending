package com.example.lifecycle_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LendingFeeRunResult(
    long loanId, LocalDate processedThrough, int daysRecorded, int billsQueued, BigDecimal billedAmount) {}
