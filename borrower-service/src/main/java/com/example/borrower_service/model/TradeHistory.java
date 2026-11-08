package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for trade_history; foreign keys are defined by Flyway. */
@Entity
@Table(name="trade_history")
@Getter
@Setter
@NoArgsConstructor
public class TradeHistory {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="order_id",nullable=false)
    private Long orderId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="side",nullable=false,length=10)
    private String side;

    @Column(name="source",nullable=false,length=20)
    private String source;

    @Column(name="quantity",nullable=false)
    private Long quantity;

    @Column(name="price",nullable=false,precision=24,scale=6)
    private BigDecimal price;

    @Column(name="value",nullable=false,precision=24,scale=6)
    private BigDecimal value;

    @Column(name="price_at",nullable=false)
    private Instant priceAt;

    @Column(name="executed_at",nullable=false)
    private Instant executedAt;

}
