package com.example.lifecycle_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LendingFeeTodayResult(
    long loanId, LocalDate accrualDate, BigDecimal marketValue, BigDecimal amount, boolean created) {}
