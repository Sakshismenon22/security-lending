package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "buyin_loan_closeout")
@Getter
@Setter
@NoArgsConstructor
public class BuyinLoanCloseout {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "loan_id")
  private Long loanId;

  @Column(name = "default_id")
  private Long defaultId;

  private Long quantity;

  @Column(name = "purchase_cost", precision = 24, scale = 6)
  private BigDecimal purchaseCost;

  @Column(name = "cash_collateral_applied", precision = 24, scale = 6)
  private BigDecimal cashCollateralApplied;

  @Column(name = "cash_collateral_returned", precision = 24, scale = 6)
  private BigDecimal cashCollateralReturned;

  private String reference;

  @Column(name = "created_at")
  private Instant createdAt;
}
