package com.example.lifecycle_service.scheduler;

import com.example.lifecycle_service.repository.*;
import com.example.lifecycle_service.service.DefaultLifecycleService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
  name = "lifecycle.default-jobs-enabled",
  havingValue = "true",
  matchIfMissing = true
)
public class DefaultDeadlineScheduler {

  private final LoanRepository loans;
  private final DefaultCaseRecordRepository cases;
  private final DefaultLifecycleService lifecycle;

  @Scheduled(fixedDelayString = "${lifecycle.default-poll-ms:5000}")
  public void check() {
    var keys = new LinkedHashMap<Long, Long>();
    loans
      .findByStatusIn(Set.of("ACTIVE", "DEFAULTED"))
      .forEach(l -> keys.put(l.getId(), l.getOrganizationId()));
    cases
      .findByStatus("OPEN")
      .forEach(c -> keys.put(c.getLoanId(), c.getOrganizationId()));
    keys.forEach((loan, org) -> {
      try {
        lifecycle.evaluate(org, loan);
      } catch (Exception e) {
        log.error("Default check failed for loan {}", loan, e);
      }
    });
  }
}
