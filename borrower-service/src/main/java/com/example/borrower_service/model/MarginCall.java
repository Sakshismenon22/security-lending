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
@Table(name="margin_recall")
@Getter
@Setter
@NoArgsConstructor
public class MarginCall {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="external_id",nullable=false,length=100)
    private String externalId;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="fulfilled_amount",nullable=false,precision=24,scale=6)
    private BigDecimal fulfilledAmount;

    @Column(name="reason",nullable=false,length=500)
    private String reason;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="request_time",nullable=false)
    private Instant requestTime;

    @Column(name="due_at",nullable=false)
    private Instant dueAt;

}
