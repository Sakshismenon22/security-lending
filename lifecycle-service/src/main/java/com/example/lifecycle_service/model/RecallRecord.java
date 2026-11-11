package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;

@Entity
@Table(name = "recall")
@Getter
public class RecallRecord {
  @Id private Long id;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "owner_reference_id")
  private Long ownerReferenceId;

  @Column(name = "due_at")
  private Instant dueAt;

  private String status;
  private Long quantity;

  @Column(name = "fulfilled_quantity")
  private Long fulfilledQuantity;

  protected RecallRecord() {}
}
