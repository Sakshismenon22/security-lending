package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for inbox_event; foreign keys are defined by Flyway. */
@Entity
@Table(name="inbox_event")
@Getter
@Setter
@NoArgsConstructor
public class InboxEvent {
    @Id
    @Column(name="event_id",nullable=false,length=120)
    private String eventId;

    @Column(name="received_at",nullable=false)
    private Instant receivedAt = Instant.now();

}
