package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lifecycle_request")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleRequest {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "loan_id")
  private Long loanId;

  @Column(name = "type", nullable = false, length = 60)
  private String type;

  @Column(name = "client_request_id", nullable = false, length = 120)
  private String clientRequestId;

  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(name = "payload", nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
