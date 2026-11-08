package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for reservation; foreign keys are defined by Flyway. */
@Entity
@Table(name="reservation")
@Getter
@Setter
@NoArgsConstructor
public class Reservation {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="locate_id",nullable=false)
    private Long locateId;

    @Column(name="msla_id",nullable=false)
    private Long mslaId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="remaining_quantity",nullable=false)
    private Long remainingQuantity;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="expires_at",nullable=false)
    private Instant expiresAt;

}
