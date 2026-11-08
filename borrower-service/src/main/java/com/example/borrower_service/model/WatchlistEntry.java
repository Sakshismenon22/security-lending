package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for watchlist_entry; foreign keys are defined by Flyway. */
@Entity
@Table(name="watchlist_entry")
@Getter
@Setter
@NoArgsConstructor
public class WatchlistEntry {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="security_id",nullable=false)
    private Long securityId;

}
