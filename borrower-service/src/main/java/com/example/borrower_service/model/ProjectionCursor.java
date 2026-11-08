package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for projection_cursor; foreign keys are defined by Flyway. */
@Entity
@Table(name="projection_cursor")
@Getter
@Setter
@NoArgsConstructor
@IdClass(ProjectionCursorId.class)
public class ProjectionCursor {
    @Id
    @Column(name="resource",nullable=false,length=80)
    private String resource;

    @Id
    @Column(name="reference_id",nullable=false)
    private Long referenceId;

    @Column(name="version",nullable=false)
    private Long version;

}
