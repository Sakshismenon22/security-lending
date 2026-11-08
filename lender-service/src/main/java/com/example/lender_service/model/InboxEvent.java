package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inbox_event")
@Getter
@Setter
@NoArgsConstructor
public class InboxEvent {
  @Id
  @Column(name = "event_id", nullable = false, length = 120)
  private String eventId;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "payload_hash", nullable = false, length = 64)
  private String payloadHash;

  @Column(name = "source", nullable = false, length = 120)
  private String source;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
