package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "borrower_connection")
@Getter
@Setter
@NoArgsConstructor
public class BorrowerConnection {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "external_id", nullable = false, length = 100)
  private String externalId;

  @Column(name = "borrower_organization_id", nullable = false)
  private Long borrowerOrganizationId;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "active", nullable = false)
  private Boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
