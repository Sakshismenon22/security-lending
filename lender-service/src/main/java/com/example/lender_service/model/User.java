package com.example.lender_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(name = "name", nullable = false, length = 160)
  private String name;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "role_id", nullable = false)
  private Long roleId;

  @Column(name = "active", nullable = false)
  private Boolean active;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;
}
