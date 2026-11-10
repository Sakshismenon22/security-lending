package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for trader_account_transaction; foreign keys are defined by Flyway. */
@Entity
@Table(name="trader_account_transaction")
@Getter
@Setter
@NoArgsConstructor
public class TraderAccountTransaction {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="type",nullable=false,length=40)
    private String type;

    @Column(name="amount",nullable=false,precision=24,scale=6)
    private BigDecimal amount;

    @Column(name="balance_after",nullable=false,precision=24,scale=6)
    private BigDecimal balanceAfter;

    @Column(name="reference",nullable=false,length=120)
    private String reference;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

}
