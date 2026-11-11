package com.example.lifecycle_service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RebateTodayResult(
    long loanId, LocalDate accrualDate, BigDecimal cashBalance, BigDecimal amount, boolean created) {}
