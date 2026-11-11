package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for loan_return; foreign keys are defined by Flyway. */
@Entity
@Table(name="loan_return")
@Getter
@Setter
@NoArgsConstructor
public class LoanReturn {
    @Column(name="recall_id")
    private Long recallId;
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="owned_quantity",nullable=false)
    private Long ownedQuantity;

    @Column(name="borrowed_quantity",nullable=false)
    private Long borrowedQuantity;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="requested_at",nullable=false)
    private Instant requestedAt = Instant.now();

    @Column(name="settled_at")
    private Instant settledAt;

    @Column(name="owned_cost",nullable=false,precision=24,scale=6)
    private BigDecimal ownedCost = BigDecimal.ZERO;

}
