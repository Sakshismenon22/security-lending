package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "rebate_payment")
@Getter
@Setter
@NoArgsConstructor
public class RebatePayment {
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

  @Column(name = "rebate_id", nullable = false)
  private Long rebateId;

  @Column(name = "amount", nullable = false, precision = 24, scale = 6)
  private BigDecimal amount;

  @Column(name = "source_event_id", nullable = false, length = 120)
  private String sourceEventId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
