package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for trade_order; foreign keys are defined by Flyway. */
@Entity
@Table(name="trade_order")
@Getter
@Setter
@NoArgsConstructor
public class TradeOrder {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="side",nullable=false,length=10)
    private String side;

    @Column(name="type",nullable=false,length=10)
    private String type;

    @Column(name="source",nullable=false,length=20)
    private String source;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="limit_price",precision=24,scale=6)
    private BigDecimal limitPrice;

    @Column(name="reserved_cash",nullable=false,precision=24,scale=6)
    private BigDecimal reservedCash = BigDecimal.ZERO;

    @Column(name="status",nullable=false,length=30)
    private String status;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

    @Column(name="filled_at")
    private Instant filledAt;

}
