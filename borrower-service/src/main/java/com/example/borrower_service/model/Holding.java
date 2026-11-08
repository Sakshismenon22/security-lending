package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for holdings; foreign keys are defined by Flyway. */
@Entity
@Table(name="holdings")
@Getter
@Setter
@NoArgsConstructor
@IdClass(HoldingId.class)
public class Holding {
    @Id
    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Id
    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="owned_quantity",nullable=false)
    private Long ownedQuantity;

    @Column(name="borrowed_quantity",nullable=false)
    private Long borrowedQuantity;

    @Column(name="pledged_quantity",nullable=false)
    private Long pledgedQuantity;

    @Column(name="blocked_owned",nullable=false)
    private Long blockedOwned;

    @Column(name="blocked_borrowed",nullable=false)
    private Long blockedBorrowed;

    @Column(name="average_cost",nullable=false,precision=24,scale=6)
    private BigDecimal averageCost;

    @Column(name="realized_pnl",nullable=false,precision=24,scale=6)
    private BigDecimal realizedPnl;

}
