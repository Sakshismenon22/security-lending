package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "loan_return")
@Getter
public class FeeLoanReturn {
  @Id
  private Long id;

  @Column(name = "loan_id")
  private Long loanId;

  private long quantity;
  private String status;

  @Column(name = "settled_at")
  private Instant settledAt;

  protected FeeLoanReturn() {}
}
