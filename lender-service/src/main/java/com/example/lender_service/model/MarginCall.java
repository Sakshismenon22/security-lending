package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "margin_recall")
@Getter
@Setter
@NoArgsConstructor
public class MarginCall implements LifecycleProjection {
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

  @Column(name = "amount", nullable = false, precision = 24, scale = 6)
  private BigDecimal amount;

  @Column(name = "fulfilled_amount", nullable = false, precision = 24, scale = 6)
  private BigDecimal fulfilledAmount;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  @Column(name = "reason", nullable = false, length = 500)
  private String reason;
}
