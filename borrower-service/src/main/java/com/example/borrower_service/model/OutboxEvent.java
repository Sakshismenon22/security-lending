package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for outbox_event; foreign keys are defined by Flyway. */
@Entity
@Table(name="outbox_event")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="event_type",nullable=false,length=80)
    private String eventType;

    @Column(name="aggregate_id",nullable=false,length=120)
    private String aggregateId;

    @Column(name="payload",nullable=false,columnDefinition="text")
    private String payload;

    @Column(name="published",nullable=false)
    private Boolean published = false;

    @Column(name="created_at",nullable=false)
    private Instant createdAt = Instant.now();

    @Column(name="ws_sent",nullable=false)
    private Boolean wsSent = false;

}
