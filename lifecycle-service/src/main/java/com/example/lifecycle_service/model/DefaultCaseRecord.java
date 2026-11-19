package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "lifecycle_default_case")
@Getter
@Setter
@NoArgsConstructor
public class DefaultCaseRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(name = "obligation_type", nullable = false)
  private String obligationType;

  @Column(name = "obligation_id", nullable = false)
  private Long obligationId;

  @Column(name = "due_at", nullable = false)
  private Instant dueAt;

  private Long quantity;

  @Column(precision = 24, scale = 6)
  private BigDecimal exposure;

  private String status;
  private Long version;
}
