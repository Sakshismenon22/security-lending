package com.example.lifecycle_service.service;

import com.example.lifecycle_service.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Manual scans use the same per-loan locked transaction as the scheduled scan. */
@Service
@RequiredArgsConstructor
public class DefaultScanService {

  private static final Logger log = LoggerFactory.getLogger(
    DefaultScanService.class
  );
  private final OrganizationRepository organizations;
  private final LoanRepository loans;
  private final DefaultCaseRecordRepository cases;
  private final DefaultLifecycleService lifecycle;
  private final Clock clock;

  public Map<String, Object> run(long org) {
    if (!organizations.existsById(org)) throw new ResponseStatusException(
      HttpStatus.NOT_FOUND,
      "Organization not found"
    );
    var ids = new LinkedHashSet<Long>();
    loans
      .findByOrganizationIdAndStatusIn(org, Set.of("ACTIVE", "DEFAULTED"))
      .forEach(l -> ids.add(l.getId()));
    cases
      .findByOrganizationIdAndStatus(org, "OPEN")
      .forEach(c -> ids.add(c.getLoanId()));
    var failed = new ArrayList<Long>();
    for (long loan : ids)
      try {
        lifecycle.evaluate(org, loan);
      } catch (Exception e) {
        failed.add(loan);
        log.error(
          "Manual default scan failed for organization {} loan {}",
          org,
          loan,
          e
        );
      }
    return Map.of(
      "organizationId",
      org,
      "loansChecked",
      ids.size(),
      "loansSucceeded",
      ids.size() - failed.size(),
      "loansFailed",
      failed.size(),
      "failedLoanIds",
      failed,
      "openCases",
      cases.countByOrganizationIdAndStatus(org, "OPEN"),
      "completedAt",
      clock.instant().toString()
    );
  }
}
