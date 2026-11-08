package com.example.borrower_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Persistence model for users; foreign keys are defined by Flyway. */
@Entity
@Table(name="users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id",nullable=false)
    private Long id;

    @Column(name="organization_id",nullable=false)
    private Long organizationId;

    @Column(name="name",nullable=false,length=160)
    private String name;

    @Column(name="email",nullable=false,length=254)
    private String email;

    @Column(name="active",nullable=false)
    private Boolean active;

    @Column(name="role_id")
    private Long roleId;

}
