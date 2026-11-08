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
@Table(name="loan_valuation")
@Getter
@Setter
@NoArgsConstructor
public class LoanValuation {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="valued_at",nullable=false)
    private Instant valuedAt;

    @Column(name="loan_market_value",nullable=false,precision=24,scale=6)
    private BigDecimal loanMarketValue;

    @Column(name="required_collateral",nullable=false,precision=24,scale=6)
    private BigDecimal requiredCollateral;

    @Column(name="recognized_collateral",nullable=false,precision=24,scale=6)
    private BigDecimal recognizedCollateral;

    @Column(name="coverage_ratio",nullable=false,precision=12,scale=6)
    private BigDecimal coverageRatio;

    @Column(name="shortfall",nullable=false,precision=24,scale=6)
    private BigDecimal shortfall;

    @Column(name="source_event_id",nullable=false,length=120)
    private String sourceEventId;

}
