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
@Table(name="loan_default")
@Getter
@Setter
@NoArgsConstructor
public class LoanDefault {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="obligation_id",nullable=false)
    private Long obligationId;

    @Column(name="obligation_type",nullable=false,length=20)
    private String obligationType;

    @Column(name="reason",nullable=false,length=500)
    private String reason;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

}
