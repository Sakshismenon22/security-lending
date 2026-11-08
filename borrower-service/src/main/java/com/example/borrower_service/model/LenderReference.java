package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for lender_reference; foreign keys are defined by Flyway. */
@Entity
@Table(name="lender_reference")
@Getter
@Setter
@NoArgsConstructor
public class LenderReference {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="name",nullable=false,length=160)
    private String name;

    @Column(name="external_id",nullable=false,length=100)
    private String externalId;

}
