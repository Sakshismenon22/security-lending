package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Borrower read projection; authoritative decisions/calculations are owned by external services. */
@Entity
@Table(name="rebate")
@Getter
@Setter
@NoArgsConstructor
public class Rebate {
    @Id
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="collateral_position_id")
    private Long collateralPositionId;

    @Column(name="rebate_rate",nullable=false,precision=16,scale=10)
    private BigDecimal rebateRate;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="accrual_date",nullable=false)
    private LocalDate accrualDate;

    @Column(name="next_payment_date",nullable=false)
    private LocalDate nextPaymentDate;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "settlement_frequency", length = 20)
    private String settlementFrequency;
}
