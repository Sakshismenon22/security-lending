package com.example.market_data_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "security_data",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_security_data_candle",
                        columnNames = {
                                "security_id",
                                "traded_date_timestamp"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_security_data_security_timestamp",
                        columnList = "security_id,traded_date_timestamp"
                )
        }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SecurityData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private SecurityMaster security;

    @Column(
            name = "traded_date_timestamp",
            nullable = false
    )
    private Instant tradedDateTimestamp;

    @Column(precision = 19, scale = 6)
    private BigDecimal open;

    @Column(precision = 19, scale = 6)
    private BigDecimal close;

    @Column(precision = 19, scale = 6)
    private BigDecimal high;

    @Column(precision = 19, scale = 6)
    private BigDecimal low;

    private Long volume;
}
