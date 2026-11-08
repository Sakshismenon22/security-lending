package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lender_account")
@Getter
@Setter
@NoArgsConstructor
public class LenderAccount {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "fee_received", nullable = false, precision = 24, scale = 6)
  private BigDecimal feeReceived;

  @Column(name = "rebate_paid", nullable = false, precision = 24, scale = 6)
  private BigDecimal rebatePaid;

  @Column(name = "cash_collateral_held", nullable = false, precision = 24, scale = 6)
  private BigDecimal cashCollateralHeld;
}
