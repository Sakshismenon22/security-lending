package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Maps the lender's immutable custody movement history. */
@Entity
@Table(name = "collateral_history")
public class CashCollateralMovement {
  @Id private Long id;
  @Column(name = "position_id") private Long positionId;
  private String type;
  @Column(name="cash_amount") private java.math.BigDecimal cashAmount;
  @Column(name = "created_at") private Instant createdAt;

  protected CashCollateralMovement() {}
}
