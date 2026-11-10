package com.example.lifecycle_service.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record CashCollateralChange(Instant recordedAt, String type, BigDecimal cashAmount) {
  public BigDecimal balanceChange() {
    return "PLEDGE_CONFIRMED".equals(type) ? cashAmount : cashAmount.negate();
  }
}
