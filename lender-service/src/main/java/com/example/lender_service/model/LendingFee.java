package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lending_fee")
@Getter
@Setter
@NoArgsConstructor
public class LendingFee implements LifecycleProjection {
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

  @Column(name = "rate", nullable = false, precision = 16, scale = 10)
  private BigDecimal rate;

  @Column(name = "period_start", nullable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false)
  private LocalDate periodEnd;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;
}
