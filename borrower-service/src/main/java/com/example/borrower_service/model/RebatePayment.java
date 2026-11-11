package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for rebate_payment; foreign keys are defined by Flyway. */
@Entity
@Table(name="rebate_payment")
@Getter
@Setter
@NoArgsConstructor
public class RebatePayment {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="rebate_id",nullable=false)
    private Long rebateId;

    @Column(name="payment_date",nullable=false)
    private Instant paymentDate = Instant.now();

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

}
