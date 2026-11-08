package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "collateral_haircut")
@Getter
@Setter
@NoArgsConstructor
public class CollateralHaircut {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Column(name = "asset_id", nullable = false)
  private Long assetId;

  @Column(name = "asset_type", nullable = false, length = 20)
  private String assetType;

  @Column(name = "security_id")
  private Long securityId;

  @Column(name = "haircut", nullable = false, precision = 12, scale = 8)
  private BigDecimal haircut;
}
