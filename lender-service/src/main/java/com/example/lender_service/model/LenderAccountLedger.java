package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lender_account_ledger")
@Getter
@Setter
@NoArgsConstructor
public class LenderAccountLedger {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "type", nullable = false, length = 50)
  private String type;

  @Column(name = "amount", nullable = false, precision = 24, scale = 6)
  private BigDecimal amount;

  @Column(name = "reference", nullable = false, length = 160)
  private String reference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
