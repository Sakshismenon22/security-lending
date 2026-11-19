package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "collateral_liquidation")
@Getter
@Setter
@NoArgsConstructor
public class CollateralLiquidation {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long organizationId;
  private Long loanId;
  private Long defaultId;
  private String clientRequestId;
  private String saleScope;
  private String reference;

  @Column(precision = 24, scale = 6)
  private BigDecimal proceeds;

  @Column(columnDefinition = "text")
  private String lots;

  private Instant createdAt;
}
