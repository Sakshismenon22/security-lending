package com.example.lifecycle_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "rebate_daily_record")
@Getter
public class RebateDay {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "loan_id", nullable = false)
  private Long loanId;

  @Column(name = "accrual_date", nullable = false)
  private LocalDate accrualDate;

  @Column(name = "cash_balance", nullable = false, precision = 24, scale = 6)
  private BigDecimal cashBalance;

  @Column(nullable = false, precision = 24, scale = 6)
  private BigDecimal amount;

  @Setter
  @Column(name = "settlement_reference_id")
  private Long settlementReferenceId;

  protected RebateDay() {}

  public RebateDay(long loanId, LocalDate accrualDate, BigDecimal cashBalance, BigDecimal amount) {
    this.loanId = loanId;
    this.accrualDate = accrualDate;
    this.cashBalance = cashBalance;
    this.amount = amount;
  }
}
