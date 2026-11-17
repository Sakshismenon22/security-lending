package com.example.borrower_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/** A transport failure is distinct from a business rejection or financial settlement. */
@Entity
@Table(name = "delivery_failure")
@Getter
@Setter
public class DeliveryFailure {

  @Id
  @Column(length = 180)
  private String id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "outbox_id", nullable = false)
  private Long outboxId;

  @Column(name = "event_id", nullable = false, length = 120)
  private String eventId;

  @Column(name = "event_type", nullable = false, length = 80)
  private String eventType;

  @Column(name = "reference_id", nullable = false, length = 120)
  private String referenceId;

  @Column(nullable = false, length = 30)
  private String status;

  @Column(nullable = false, length = 30)
  private String source;

  @Column(nullable = false, length = 30)
  private String stage;

  @Column(name = "transport_id", nullable = false)
  private Long transportId;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
