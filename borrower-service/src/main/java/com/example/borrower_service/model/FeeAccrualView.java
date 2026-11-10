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
@Table(name="fee_accrual_view")
@Getter
@Setter
@NoArgsConstructor
public class FeeAccrualView {
    @Id
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="loan_id",nullable=false)
    private Long loanId;

    @Column(name="accrual_date",nullable=false)
    private LocalDate accrualDate;

    @Column(name="type",nullable=false,length=20)
    private String type;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="base_value",nullable=false,precision=24,scale=6)
    private BigDecimal baseValue;

    @Column(name="rate",nullable=false,precision=16,scale=10)
    private BigDecimal rate;

    @Column(name="source_event_id",nullable=false,length=120)
    private String sourceEventId;

}
