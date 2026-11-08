package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "collateral_history")
@Getter
@Setter
@NoArgsConstructor
public class CollateralHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "position_id", nullable = false)
  private Long positionId;

  @Column(name = "type", nullable = false, length = 40)
  private String type;

  @Column(name = "reference", nullable = false, length = 160)
  private String reference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
