package com.example.lifecycle_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The lender organization row used to serialize changes in the shared database. */
@Entity
@Table(name = "organization")
public class Organization {
  @Id private Long id;

  protected Organization() {}
}
