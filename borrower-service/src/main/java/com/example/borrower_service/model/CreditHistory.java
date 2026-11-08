package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for credit_history; foreign keys are defined by Flyway. */
@Entity
@Table(name="credit_history")
@Getter
@Setter
@NoArgsConstructor
public class CreditHistory {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="msla_id",nullable=false)
    private Long mslaId;

    @Column(name="transaction_type",nullable=false,length=40)
    private String transactionType;

    @Column(name="old_limit",nullable=false,precision=24,scale=6)
    private BigDecimal oldLimit;

    @Column(name="new_limit",nullable=false,precision=24,scale=6)
    private BigDecimal newLimit;

    @Column(name="credit_change",nullable=false,precision=24,scale=6)
    private BigDecimal creditChange;

    @Column(name="utilized_before",nullable=false,precision=24,scale=6)
    private BigDecimal utilizedBefore;

    @Column(name="utilized_after",nullable=false,precision=24,scale=6)
    private BigDecimal utilizedAfter;

    @Column(name="available_before",nullable=false,precision=24,scale=6)
    private BigDecimal availableBefore;

    @Column(name="available_after",nullable=false,precision=24,scale=6)
    private BigDecimal availableAfter;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

}
