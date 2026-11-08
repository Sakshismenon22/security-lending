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
@Table(name="lending_fee")
@Getter
@Setter
@NoArgsConstructor
public class LendingFee {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="period_start",nullable=false)
    private LocalDate periodStart;

    @Column(name="period_end",nullable=false)
    private LocalDate periodEnd;

    @Column(name="fee_rate",nullable=false,precision=16,scale=10)
    private BigDecimal feeRate;

    @Column(name="fee_amount",nullable=false,precision=24,scale=6)
    private BigDecimal feeAmount;

    @Column(name="currency",nullable=false,length=3)
    private String currency;

    @Column(name="due_date",nullable=false)
    private LocalDate dueDate;

    @Column(name="paid_at")
    private Instant paidAt;

    @Column(name="status",nullable=false,length=30)
    private String status;

}
