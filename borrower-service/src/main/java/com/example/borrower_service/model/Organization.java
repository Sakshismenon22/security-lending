package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for organization; foreign keys are defined by Flyway. */
@Entity
@Table(name="organization")
@Getter
@Setter
@NoArgsConstructor
public class Organization {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="name",nullable=false,length=160)
    private String name;

    @Column(name="registration_number",nullable=false,length=100)
    private String registrationNumber;

    @Column(name="currency",nullable=false,length=3)
    private String currency;

    @Column(name="created_at",nullable=false)
    private Instant createdAt;

}
