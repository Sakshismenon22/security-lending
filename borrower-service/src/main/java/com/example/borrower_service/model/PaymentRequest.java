package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for payment_request; foreign keys are defined by Flyway. */
@Entity
@Table(name="payment_request")
@Getter
@Setter
@NoArgsConstructor
public class PaymentRequest {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="fee_id")
    private Long feeId;

    @Column(name="type",nullable=false,length=30)
    private String type;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="client_request_id",nullable=false,length=120)
    private String clientRequestId;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

    @Column(name="confirmed_at")
    private Instant confirmedAt;

}
