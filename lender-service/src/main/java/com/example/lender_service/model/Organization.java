package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organization")
@Getter
@Setter
@NoArgsConstructor
public class Organization {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "external_id", nullable = false, length = 100)
  private String externalId;

  @Column(name = "public_reference_id", nullable = false)
  private Long publicReferenceId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
