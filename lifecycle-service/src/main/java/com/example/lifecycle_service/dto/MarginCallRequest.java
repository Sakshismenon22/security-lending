package com.example.lifecycle_service.dto;

import java.math.BigDecimal;

public record MarginCallRequest(Long loanId, BigDecimal amount) {}
