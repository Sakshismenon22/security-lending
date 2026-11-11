package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "loan_valuation")
@Getter
@Setter
@NoArgsConstructor
public class LoanValuation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(name = "owner_reference_id", nullable = false)
  private Long ownerReferenceId;

  @Column(name = "source_version", nullable = false)
  private Long sourceVersion;

  @Column(name = "source_data", nullable = false, columnDefinition = "text")
  private String sourceData;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "loan_market_value", nullable = false, precision = 24, scale = 6)
  private BigDecimal loanMarketValue;

  @Column(name = "required_collateral", nullable = false, precision = 24, scale = 6)
  private BigDecimal requiredCollateral;

  @Column(name = "recognized_collateral", nullable = false, precision = 24, scale = 6)
  private BigDecimal recognizedCollateral;

  @Column(name = "coverage_ratio", nullable = false, precision = 12, scale = 6)
  private BigDecimal coverageRatio;

  @Column(name = "shortfall", nullable = false, precision = 24, scale = 6)
  private BigDecimal shortfall;

  @Column(name = "valued_at", nullable = false)
  private Instant valuedAt;
}
