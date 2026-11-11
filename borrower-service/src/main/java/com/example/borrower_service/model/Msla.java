package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for msla; foreign keys are defined by Flyway. */
@Entity
@Table(name="msla")
@Getter
@Setter
@NoArgsConstructor
public class Msla {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="lender_id",nullable=false)
    private Long lenderId;

    @Column(name="number",nullable=false,length=100)
    private String number;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="effective_date",nullable=false)
    private LocalDate effectiveDate;

    @Column(name="expiry_date",nullable=false)
    private LocalDate expiryDate;

    @Column(name="credit_limit",nullable=false,precision=24,scale=6)
    private BigDecimal creditLimit;

    @Column(name="fee_rate",nullable=false,precision=16,scale=10)
    private BigDecimal feeRate;

    @Column(name="rebate_rate",nullable=false,precision=16,scale=10)
    private BigDecimal rebateRate;

    @Column(name="coverage_ratio",nullable=false,precision=12,scale=6)
    private BigDecimal coverageRatio;

    @Column(name="billing_frequency",nullable=false,length=20)
    private String billingFrequency;

    @Column(name = "rebate_settlement_frequency", nullable = false, length = 20)
    private String rebateSettlementFrequency = "AT_CLOSE";

    @Column(name="day_basis",nullable=false)
    private Integer dayBasis;

    @Column(name="recall_allowed",nullable=false)
    private Boolean recallAllowed;

    @Column(name="margin_hours",nullable=false)
    private Integer marginHours;

    @Column(name="recall_hours",nullable=false)
    private Integer recallHours;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

}
