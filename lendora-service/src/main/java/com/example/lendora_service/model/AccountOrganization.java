package com.example.lendora_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "auth_organization")
@Getter
@Setter
public class AccountOrganization {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "external_key", nullable = false, length = 80)
  private String externalKey;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false, length = 20)
  private String type;

  @Column(name = "registration_number", nullable = false, length = 100)
  private String registrationNumber;

  @Column(nullable = false, length = 80)
  private String country;

  @Column(name = "base_currency", nullable = false, length = 3)
  private String baseCurrency;

  @Column(name = "registered_address", nullable = false, length = 500)
  private String registeredAddress;

  @Column(name = "platform_organization_id")
  private Long platformOrganizationId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
