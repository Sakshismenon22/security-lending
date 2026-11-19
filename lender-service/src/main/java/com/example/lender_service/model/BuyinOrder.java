package com.example.lender_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity
@Table(name = "buyin_order")
@Getter
@Setter
@NoArgsConstructor
public class BuyinOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "default_id", nullable = false)
  private Long defaultId;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(name = "security_id", nullable = false)
  private Long securityId;

  @Column(name = "buyin_request_id", nullable = false)
  private Long buyinRequestId;

  @Column(name = "execution_request_id")
  private Long executionRequestId;

  @Column(name = "client_request_id", nullable = false)
  private String clientRequestId;

  private String type;
  private Long quantity;

  @Column(name = "limit_price", precision = 24, scale = 6)
  private BigDecimal limitPrice;

  @Column(precision = 24, scale = 6)
  private BigDecimal costs;

  private String status;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "execution_price", precision = 24, scale = 6)
  private BigDecimal executionPrice;

  @Column(name = "quote_at")
  private Instant quoteAt;

  @Column(name = "execution_reference", nullable = false)
  private String executionReference;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "filled_at")
  private Instant filledAt;
}
