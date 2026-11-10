package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for collateral_position; foreign keys are defined by Flyway. */
@Entity
@Table(name="collateral_position")
@Getter
@Setter
@NoArgsConstructor
public class CollateralPosition {
    @Column(name="margin_call_id")
    private Long marginCallId;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="asset_type",nullable=false,length=20)
    private String assetType;

    @Column(name="security_id")
    private Long securityId;

    @Column(name="quantity",nullable=false)
    private Long quantity = 0L;

    @Column(name="cash_amount",nullable=false,precision=24,scale=6)
    private BigDecimal cashAmount = BigDecimal.ZERO;

    @Column(name="haircut",nullable=false,precision=12,scale=8)
    private BigDecimal haircut;

    @Column(name="status",nullable=false,length=30)
    private String status = "POSTED";

    @Column(name="asset_id")
    private Long assetId;

}
