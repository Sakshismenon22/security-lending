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
@Table(name="buy_in")
@Getter
@Setter
@NoArgsConstructor
public class BuyIn {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="default_id",nullable=false)
    private Long defaultId;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="price",nullable=false,precision=24,scale=6)
    private BigDecimal price;

    @Column(name="costs",nullable=false,precision=24,scale=6)
    private BigDecimal costs;

    @Column(name="collateral_applied",nullable=false,precision=24,scale=6)
    private BigDecimal collateralApplied;

    @Column(name="residual_balance",nullable=false,precision=24,scale=6)
    private BigDecimal residualBalance;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

}
