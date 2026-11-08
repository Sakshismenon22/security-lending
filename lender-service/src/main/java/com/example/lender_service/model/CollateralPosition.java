package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "collateral_position")
@Getter
@Setter
@NoArgsConstructor
public class CollateralPosition {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Column(name = "loan_id")
  private Long loanId;

  @Column(name = "borrower_loan_id", nullable = false)
  private Long borrowerLoanId;

  @Column(name = "borrower_position_id", nullable = false)
  private Long borrowerPositionId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "asset_type", nullable = false, length = 20)
  private String assetType;

  @Column(name = "security_id")
  private Long securityId;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "cash_amount", nullable = false, precision = 24, scale = 6)
  private BigDecimal cashAmount;

  @Column(name = "haircut", nullable = false, precision = 12, scale = 8)
  private BigDecimal haircut;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "settlement_reference", length = 160)
  private String settlementReference;

  @Column(name = "margin_call_id")
  private Long marginCallId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
