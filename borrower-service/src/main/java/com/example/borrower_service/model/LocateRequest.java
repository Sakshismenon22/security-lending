package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for locate_request; foreign keys are defined by Flyway. */
@Entity
@Table(name="locate_request")
@Getter
@Setter
@NoArgsConstructor
public class LocateRequest {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="msla_id",nullable=false)
    private Long mslaId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="approved_quantity",nullable=false)
    private Long approvedQuantity = 0L;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="expires_at",nullable=false)
    private Instant expiresAt;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

}
