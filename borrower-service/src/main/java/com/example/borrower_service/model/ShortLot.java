package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for short_lot; foreign keys are defined by Flyway. */
@Entity
@Table(name="short_lot")
@Getter
@Setter
@NoArgsConstructor
public class ShortLot {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

    @Column(name="order_id",nullable=false)
    private Long orderId;

    @Column(name="open_quantity",nullable=false)
    private Long openQuantity;

    @Column(name="price",nullable=false,precision=24,scale=6)
    private BigDecimal price;

}
