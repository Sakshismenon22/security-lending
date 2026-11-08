package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "msla_id", nullable = false)
  private Long mslaId;

  @Column(name = "locate_id", nullable = false)
  private Long locateId;

  @Column(name = "security_id", nullable = false)
  private Long securityId;

  @Column(name = "borrower_reservation_id", nullable = false)
  private Long borrowerReservationId;

  @Column(name = "quantity", nullable = false)
  private Long quantity;

  @Column(name = "remaining_quantity", nullable = false)
  private Long remainingQuantity;

  @Column(name = "revision", nullable = false)
  private Long revision;

  @Column(name = "status", nullable = false, length = 40)
  private String status;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
