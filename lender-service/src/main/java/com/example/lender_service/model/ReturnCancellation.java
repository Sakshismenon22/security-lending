package com.example.lender_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Durable receiver fence: a cancelled return must never execute after its assets were released. */
@Entity
@Table(
  name = "return_cancellation",
  uniqueConstraints = @UniqueConstraint(
    columnNames = { "connection_id", "borrower_return_id" }
  )
)
@Getter
@Setter
public class ReturnCancellation {

  @Id
  @Column(name = "event_id", length = 120)
  private String eventId;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "borrower_return_id", nullable = false)
  private Long borrowerReturnId;

  @Column(nullable = false, columnDefinition = "text")
  private String confirmation;
}
