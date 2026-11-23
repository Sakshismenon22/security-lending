package com.example.borrower_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "buyin_closure_summary")
@Getter
@Setter
@NoArgsConstructor
public class BuyinClosureSummary {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(nullable = false)
  private Long quantity;

  @Column(name = "purchase_value", nullable = false, precision = 24, scale = 6)
  private BigDecimal purchaseValue;

  @Column(name = "execution_costs", nullable = false, precision = 24, scale = 6)
  private BigDecimal executionCosts;

  @Column(name = "purchase_cost", nullable = false, precision = 24, scale = 6)
  private BigDecimal purchaseCost;

  @Column(
    name = "cash_collateral_applied",
    nullable = false,
    precision = 24,
    scale = 6
  )
  private BigDecimal cashCollateralApplied;

  @Column(
    name = "cash_collateral_returned",
    nullable = false,
    precision = 24,
    scale = 6
  )
  private BigDecimal cashCollateralReturned;

  @Column(name = "average_price", nullable = false, precision = 24, scale = 6)
  private BigDecimal averagePrice;

  @Column(nullable = false, length = 120)
  private String reference;

  @Column(name = "closed_at", nullable = false)
  private Instant closedAt;

  public BigDecimal getLenderFundsApplied() {
    return purchaseCost.subtract(cashCollateralApplied);
  }
}
