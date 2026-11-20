package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lender_deposit_request")
@Getter
@Setter
public class LenderDepositRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private Long organizationId;
  private String clientRequestId;
  private BigDecimal amount;
  private String status;
  private Instant createdAt;
  private Instant approvedAt;
  private Long approvedBy;
}
