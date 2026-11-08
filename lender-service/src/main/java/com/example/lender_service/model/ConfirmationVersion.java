package com.example.lender_service.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "confirmation_version")
@Getter
@Setter
@NoArgsConstructor
public class ConfirmationVersion {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "connection_id", nullable = false)
  private Long connectionId;

  @Column(name = "resource", nullable = false, length = 80)
  private String resource;

  @Column(name = "reference_id", nullable = false)
  private Long referenceId;

  @Column(name = "sequence_number", nullable = false)
  private Long sequenceNumber;
}
