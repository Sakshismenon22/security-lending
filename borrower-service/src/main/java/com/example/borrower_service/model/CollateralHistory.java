package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for collateral_history; foreign keys are defined by Flyway. */
@Entity
@Table(name="collateral_history")
@Getter
@Setter
@NoArgsConstructor
public class CollateralHistory {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="position_id",nullable=false)
    private Long positionId;

    @Column(name="type",nullable=false,length=30)
    private String type;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

    @Column(name="asset_id")
    private Long assetId;

    @Column(name="security_id")
    private Long securityId;

}
