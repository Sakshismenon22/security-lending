package com.example.market_data_service.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "security_current_price",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_security_current_price",
                        columnNames = "security_id"
                )
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SecuritiesCurrentPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "security_id",
            nullable = false,
            unique = true
    )
    private SecurityMaster security;

    @Column(
            name = "trade_date_timestamp",
            nullable = false
    )
    private Instant tradeDateTimestamp;

    @Column(precision = 19, scale = 6)
    private BigDecimal open;

    @Column(precision = 19, scale = 6)
    private BigDecimal high;

    @Column(precision = 19, scale = 6)
    private BigDecimal low;

    @Column(precision = 19, scale = 6)
    private BigDecimal close;

    private Long volume;

    @Column(nullable = false)
    private Instant updatedAt;
}