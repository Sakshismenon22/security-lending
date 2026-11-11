package com.example.lifecycle_service.scheduler;

import com.example.lifecycle_service.repository.LoanRepository;
import com.example.lifecycle_service.service.MarginCoverageService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarginCoverageScheduler {
  private final LoanRepository loanRepository;
  private final MarginCoverageService marginCoverageService;

  @Scheduled(cron = "${lifecycle.margin-check-cron:0 15 15 * * *}")
  public void runCoverageCheck() {
    List<com.example.lifecycle_service.model.Loan> activeLoans = loanRepository.findAllByStatus("ACTIVE");
    for (var loan : activeLoans) {
      var decision = marginCoverageService.evaluateLoan(loan.getId());
      if (decision.requiresMarginCall()) {
        marginCoverageService.triggerMarginCallForLoan(loan.getId(), decision.getMarginCallAmount());
      }
    }
  }
}
