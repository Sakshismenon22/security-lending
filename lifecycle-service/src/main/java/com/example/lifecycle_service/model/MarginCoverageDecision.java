package com.example.lifecycle_service.model;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarginCoverageDecision {
  private Long loanId;
  private Long organizationId;
  private BigDecimal loanMarketValue;
  private BigDecimal recognizedCollateral;
  private BigDecimal requiredCollateral;
  private BigDecimal coverageRatio;
  private BigDecimal shortfall;
  private BigDecimal marginCallAmount;
  private String status;
  private String reason;
  private Instant evaluatedAt;

  public boolean requiresMarginCall() {
    return shortfall != null && shortfall.signum() > 0;
  }
}
