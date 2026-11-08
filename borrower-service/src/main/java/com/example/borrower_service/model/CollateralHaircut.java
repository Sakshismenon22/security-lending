package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for collateral_haircut; foreign keys are defined by Flyway. */
@Entity
@Table(name="collateral_haircut")
@Getter
@Setter
@NoArgsConstructor
public class CollateralHaircut {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="msla_id",nullable=false)
    private Long mslaId;

    @Column(name="asset_type",nullable=false,length=20)
    private String assetType;

    @Column(name="security_id")
    private Long securityId;

    @Column(name="haircut",nullable=false,precision=12,scale=8)
    private BigDecimal haircut;

    @Column(name="asset_id")
    private Long assetId;

}
