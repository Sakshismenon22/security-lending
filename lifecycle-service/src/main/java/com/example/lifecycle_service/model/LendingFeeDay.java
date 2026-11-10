package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lending_fee_day")
@Getter
public class LendingFeeDay {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(name = "accrual_date", nullable = false)
  private LocalDate accrualDate;

  @Column(name = "market_value", nullable = false, precision = 24, scale = 6)
  private BigDecimal marketValue;

  @Column(nullable = false, precision = 24, scale = 6)
  private BigDecimal amount;

  @Setter
  @Column(nullable = false)
  private boolean billed;

  protected LendingFeeDay() {}

  public LendingFeeDay(long loanId, LocalDate accrualDate, BigDecimal marketValue, BigDecimal amount) {
    this.loanId = loanId;
    this.accrualDate = accrualDate;
    this.marketValue = marketValue;
    this.amount = amount;
  }
}
