package com.example.lifecycle_service.service;

import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deadline decisions belong to lifecycle. Projection writes remain owned by lender-service. */
@Service
@RequiredArgsConstructor
public class DefaultLifecycleService {

  private final LoanRepository loans;
  private final RecallRecordRepository recalls;
  private final MarginCallRepository margins;
  private final DefaultCaseRecordRepository cases;
  private final LenderOutboxEventRepository outbox;
  private final ObjectMapper json;
  private final Clock clock;

  @Value("${lifecycle.lender-events-topic:lender.lifecycle.events.v1}")
  private String topic;

  private static final Set<String> OPEN = Set.of(
    "OPEN",
    "PENDING",
    "PARTIALLY_FULFILLED",
    "OVERDUE"
  );

  @Transactional(rollbackFor = Exception.class)
  public void evaluate(long org, long loanId) throws Exception {
    if (loans.lockOrganization(org) == null) return;
    var loan = loans.findByIdAndOrganizationId(loanId, org).orElse(null);
    if (loan == null) return;
    var existing = cases.findByOrganizationIdAndLoanId(org, loanId);
    var seen = new HashSet<Long>();
    boolean active =
      Set.of("ACTIVE", "DEFAULTED").contains(loan.getStatus()) &&
      loan.getSettledAt() != null &&
      loan.getOutstandingQuantity() > 0;
    if (active) {
      for (var r : recalls.findByLoanId(loanId))
        if (
          r.getOrganizationId() == org &&
          OPEN.contains(r.getStatus()) &&
          r.getQuantity() > r.getFulfilledQuantity()
        ) detect(
          loan,
          existing,
          seen,
          "RECALL",
          r.getOwnerReferenceId(),
          r.getDueAt(),
          Math.min(
            loan.getOutstandingQuantity(),
            r.getQuantity() - r.getFulfilledQuantity()
          ),
          BigDecimal.ZERO
        );
      for (var m : margins.findByLoanId(loanId))
        if (
          m.getOrganizationId() == org &&
          OPEN.contains(m.getStatus()) &&
          m.getAmount().compareTo(m.getFulfilledAmount()) > 0
        ) detect(
          loan,
          existing,
          seen,
          "MARGIN_CALL",
          m.getOwnerReferenceId(),
          m.getDueAt(),
          0,
          m.getAmount().subtract(m.getFulfilledAmount())
        );
      if (loan.getMaturityAt() != null) detect(
        loan,
        existing,
        seen,
        "MATURITY",
        loanId,
        loan.getMaturityAt(),
        loan.getOutstandingQuantity(),
        BigDecimal.ZERO
      );
    }
    for (var c : existing)
      if ("OPEN".equals(c.getStatus()) && !seen.contains(c.getId())) {
        c.setStatus("RESOLVED");
        c.setQuantity(0L);
        c.setExposure(BigDecimal.ZERO);
        publish(c);
      }
  }

  private void detect(
    Loan loan,
    List<DefaultCaseRecord> existing,
    Set<Long> seen,
    String type,
    long obligation,
    Instant due,
    long quantity,
    BigDecimal exposure
  ) throws Exception {
    if (due == null || !clock.instant().isAfter(due)) return;
    var c = existing
      .stream()
      .filter(
        x ->
          x.getObligationType().equals(type) &&
          x.getObligationId() == obligation &&
          x.getDueAt().equals(due)
      )
      .findFirst()
      .orElse(null);
    boolean changed = c == null;
    if (c == null) {
      c = new DefaultCaseRecord();
      c.setOrganizationId(loan.getOrganizationId());
      c.setLoanId(loan.getId());
      c.setObligationType(type);
      c.setObligationId(obligation);
      c.setDueAt(due);
      c.setVersion(0L);
    } else changed =
      !"OPEN".equals(c.getStatus()) ||
      c.getQuantity() != quantity ||
      c.getExposure().compareTo(exposure) != 0;
    c.setStatus("OPEN");
    c.setQuantity(quantity);
    c.setExposure(exposure);
    cases.saveAndFlush(c);
    seen.add(c.getId());
    if (changed) publish(c);
  }

  private void publish(DefaultCaseRecord c) throws Exception {
    c.setVersion(c.getVersion() + 1);
    var body = new LinkedHashMap<String, Object>();
    body.put("loanId", c.getLoanId());
    body.put("obligationType", c.getObligationType());
    body.put("obligationId", c.getObligationId());
    body.put(
      "reason",
      "RESOLVED".equals(c.getStatus())
        ? "Obligation cured or loan closed"
        : "Outstanding obligation past its contractual deadline"
    );
    body.put("dueAt", c.getDueAt().toString());
    body.put("quantity", c.getQuantity());
    body.put("status", c.getStatus());
    if ("MARGIN_CALL".equals(c.getObligationType())) body.put(
      "exposure",
      c.getExposure()
    );
    emit(
      c.getOrganizationId(),
      c.getId(),
      c.getVersion(),
      "default-" + c.getId() + "-" + c.getVersion(),
      "DEFAULT_REPORTED",
      body
    );
  }

  public void emit(
    long org,
    long reference,
    long version,
    String eventId,
    String type,
    Map<String, Object> data
  ) throws Exception {
    outbox.save(
      new LenderOutboxEvent(
        org,
        eventId,
        topic,
        json.writeValueAsString(
          Map.of(
            "eventId",
            eventId,
            "type",
            type,
            "organizationId",
            org,
            "referenceId",
            reference,
            "version",
            version,
            "occurredAt",
            clock.instant().toString(),
            "data",
            data
          )
        ),
        clock.instant()
      )
    );
  }
}
