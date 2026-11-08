package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "buy_in")
@Getter
@Setter
@NoArgsConstructor
public class BuyIn implements LifecycleProjection {
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

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "source_data", nullable = false, columnDefinition = "text")
  private String sourceData;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "default_reference_id", nullable = false)
  private Long defaultReferenceId;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "price", nullable = false, precision = 24, scale = 6)
  private BigDecimal price;

  @Column(name = "costs", nullable = false, precision = 24, scale = 6)
  private BigDecimal costs;

  @Column(name = "collateral_applied", nullable = false, precision = 24, scale = 6)
  private BigDecimal collateralApplied;

  @Column(name = "residual_balance", nullable = false, precision = 24, scale = 6)
  private BigDecimal residualBalance;
}
