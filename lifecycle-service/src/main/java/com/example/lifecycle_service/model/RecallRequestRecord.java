package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "lifecycle_request")
@Getter
public class RecallRequestRecord {
  @Id private Long id;

  @Column(name = "organization_id")
  private Long organizationId;

  @Column(name = "connection_id")
  private Long connectionId;

  @Column(name = "loan_id")
  private Long loanId;

  private String type;
  private String status;

  @Column(columnDefinition = "text")
  private String payload;

  protected RecallRequestRecord() {}
}
