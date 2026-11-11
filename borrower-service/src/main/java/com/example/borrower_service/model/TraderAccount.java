package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for trader_account; foreign keys are defined by Flyway. */
@Entity
@Table(name="trader_account")
@Getter
@Setter
@NoArgsConstructor
public class TraderAccount {
    @Id
    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="available",nullable=false,precision=24,scale=6)
    private BigDecimal available = BigDecimal.ZERO;

    @Column(name="blocked",nullable=false,precision=24,scale=6)
    private BigDecimal blocked = BigDecimal.ZERO;

    @Column(name="pledged",nullable=false,precision=24,scale=6)
    private BigDecimal pledged = BigDecimal.ZERO;

}
