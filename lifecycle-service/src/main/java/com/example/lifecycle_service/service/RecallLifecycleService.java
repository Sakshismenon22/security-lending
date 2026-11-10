package com.example.lifecycle_service.service;

import com.example.lifecycle_service.model.LenderOutboxEvent;
import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecallLifecycleService {
  public static final Set<String> DEADLINE_STATUSES = Set.of("OPEN", "PARTIALLY_FULFILLED");

  private final LoanRepository loans;
  private final MslaRepository agreements;
  private final RecallRequestRecordRepository requests;
  private final RecallRecordRepository recalls;
  private final LenderOutboxEventRepository outbox;
  private final ObjectMapper json;

  @Value("${lifecycle.lender-events-topic:lender.lifecycle.events.v1}")
  private String topic;

  @Transactional
  public void issue(long org, long requestId) throws JsonProcessingException {
    if (loans.lockOrganization(org) == null)
      throw new IllegalArgumentException("Unknown organization");
    var request =
        requests
            .findByIdAndOrganizationId(requestId, org)
            .orElseThrow(() -> new IllegalArgumentException("Unknown recall request"));
    if (!"RECALL_REQUESTED".equals(request.getType()))
      throw new IllegalArgumentException("Lifecycle request is not a recall");
    if (!Set.of("PENDING", "ACCEPTED").contains(request.getStatus())) return;

    String eventId = "recall-issue-" + requestId;
    if (outbox.existsByEventId(eventId)) return;
    var loan = loans.findByIdAndOrganizationId(request.getLoanId(), org).orElse(null);
    var agreement = loan == null ? null : agreements.findById(loan.getMslaId()).orElse(null);
    var terms = json.readTree(request.getPayload());
    long quantity = terms.path("quantity").asLong();
    String reason = terms.path("reason").asText();
    Instant now = Instant.now();

    if (loan == null
        || !request.getConnectionId().equals(loan.getConnectionId())
        || !"ACTIVE".equals(loan.getStatus())
        || agreement == null
        || !Boolean.TRUE.equals(agreement.getRecallAllowed())
        || quantity <= 0
        || quantity > loan.getOutstandingQuantity()
        || reason.isBlank()) {
      enqueue(
          org,
          requestId,
          eventId,
          "LIFECYCLE_REQUEST_REJECTED",
          now,
          Map.of("reason", "Recall no longer meets the loan or MSLA requirements"));
      return;
    }

    enqueue(
        org,
        requestId,
        eventId,
        "RECALL_UPDATED",
        now,
        Map.of(
            "loanId",
            loan.getId(),
            "lifecycleRequestId",
            requestId,
            "externalId",
            "RECALL-" + requestId,
            "quantity",
            quantity,
            "fulfilledQuantity",
            0,
            "status",
            "OPEN",
            "reason",
            reason,
            "dueAt",
            now.plus(agreement.getRecallHours(), ChronoUnit.HOURS).toString()));
  }

  @Transactional
  public void reportDeadline(long org, long recallId) throws JsonProcessingException {
    if (loans.lockOrganization(org) == null) return;
    var recall = recalls.findById(recallId).orElse(null);
    Instant now = Instant.now();
    if (recall == null
        || recall.getOrganizationId() != org
        || recall.getDueAt().isAfter(now)
        || !DEADLINE_STATUSES.contains(recall.getStatus())
        || recall.getFulfilledQuantity() >= recall.getQuantity()) return;
    String eventId = "recall-due-" + org + "-" + recallId + "-" + recall.getDueAt();
    if (outbox.existsByEventId(eventId)) return;
    enqueue(
        org,
        recall.getOwnerReferenceId(),
        eventId,
        "RECALL_DEADLINE_REACHED",
        now,
        Map.of("expectedDueAt", recall.getDueAt().toString()));
  }

  private void enqueue(
      long org,
      long referenceId,
      String eventId,
      String type,
      Instant now,
      Map<String, Object> data)
      throws JsonProcessingException {
    String payload =
        json.writeValueAsString(
            Map.of(
                "eventId",
                eventId,
                "type",
                type,
                "organizationId",
                org,
                "referenceId",
                referenceId,
                "version",
                1,
                "occurredAt",
                now.toString(),
                "data",
                data));
    outbox.save(new LenderOutboxEvent(org, eventId, topic, payload, now));
  }
}
