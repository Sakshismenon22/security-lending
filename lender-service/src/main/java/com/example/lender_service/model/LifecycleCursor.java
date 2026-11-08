package com.example.lender_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "lifecycle_cursor")
@Getter
@Setter
@NoArgsConstructor
public class LifecycleCursor {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "resource", nullable = false, length = 80)
  private String resource;

  @Column(name = "reference_id", nullable = false)
  private Long referenceId;

  @Column(name = "source_version", nullable = false)
  private Long sourceVersion;
}
