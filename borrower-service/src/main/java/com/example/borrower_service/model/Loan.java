package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for loan; foreign keys are defined by Flyway. */
@Entity
@Table(name="loan")
@Getter
@Setter
@NoArgsConstructor
public class Loan {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="msla_id",nullable=false)
    private Long mslaId;

    @Column(name="reservation_id",nullable=false)
    private Long reservationId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="outstanding_quantity",nullable=false)
    private Long outstandingQuantity;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="fee_rate",nullable=false,precision=16,scale=10)
    private BigDecimal feeRate;

    @Column(name="rebate_rate",nullable=false,precision=16,scale=10)
    private BigDecimal rebateRate;

    @Column(name="coverage_ratio",nullable=false,precision=12,scale=6)
    private BigDecimal coverageRatio;

    @Column(name="day_basis",nullable=false)
    private Integer dayBasis;

    @Column(name="billing_frequency",nullable=false,length=20)
    private String billingFrequency;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

    @Column(name="settled_at")
    private Instant settledAt;

    @Column(name="closed_at")
    private Instant closedAt;

    @Column(name="settled_on")
    private LocalDate settledOn;

}
