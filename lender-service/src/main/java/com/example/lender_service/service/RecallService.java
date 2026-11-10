package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Recall changes run inside the caller's lender transaction and organization lock. */
@Service
@RequiredArgsConstructor
public class RecallService {
  private static final Set<String> OUTSTANDING = Set.of("OPEN", "PARTIALLY_FULFILLED", "OVERDUE");
  private final RecallRepository recalls;
  private final LoanReturnRepository returns;
  private final LoanRepository loans;
  private final BorrowerConnectionRepository connections;
  private final EventService events;
  private final Clock clock;

  long outstanding(long loanId) {
    return recalls.findByLoanIdAndStatusInOrderByDueAtAscIdAsc(loanId, OUTSTANDING).stream()
        .mapToLong(this::remaining)
        .sum();
  }

  void validateReturn(Loan loan, long ownerReference, long quantity) {
    var recall = linkedRecall(loan, ownerReference);
    require(OUTSTANDING.contains(recall.getStatus()), "Recall is already resolved");
    require(
        quantity <= remaining(recall) - pendingFor(recall),
        "Return exceeds uncommitted recall quantity");
  }

  void applySettlement(Loan loan, LoanReturn returned) {
    long quantity = returned.getQuantity();
    if (returned.getRecallId() != null) {
      var recall = linkedRecall(loan, returned.getRecallId());
      if (OUTSTANDING.contains(recall.getStatus())) {
        quantity -= credit(recall, loan, quantity);
      }
    }
    // Untagged returns also reduce the lender's outstanding recall obligations.
    // Keep any quantity already assigned to another pending linked settlement.
    for (var recall :
        recalls.findByLoanIdAndStatusInOrderByDueAtAscIdAsc(loan.getId(), OUTSTANDING)) {
      if (quantity == 0) break;
      long uncommitted = Math.max(0, remaining(recall) - pendingFor(recall));
      quantity -= credit(recall, loan, Math.min(quantity, uncommitted));
    }
  }

  void deadlineReached(long org, long ownerReference, Instant expectedDueAt) {
    var recall = recalls.findByOrganizationIdAndOwnerReferenceId(org, ownerReference).orElse(null);
    if (recall == null
        || !Set.of("OPEN", "PARTIALLY_FULFILLED").contains(recall.getStatus())
        || !recall.getDueAt().equals(expectedDueAt)
        || clock.instant().isBefore(expectedDueAt)
        || remaining(recall) == 0) return;
    recall.setStatus("OVERDUE");
    publish(recall, found(loans.findByIdAndOrganizationId(recall.getLoanId(), org)));
  }

  private Recall linkedRecall(Loan loan, long ownerReference) {
    var recall =
        found(
            recalls.findByOrganizationIdAndOwnerReferenceId(
                loan.getOrganizationId(), ownerReference));
    require(
        Objects.equals(recall.getLoanId(), loan.getId())
            && Objects.equals(recall.getConnectionId(), loan.getConnectionId()),
        "Recall does not belong to this loan and borrower connection");
    return recall;
  }

  private long pendingFor(Recall recall) {
    return returns
        .findByLoanIdAndRecallIdAndStatus(
            recall.getLoanId(), recall.getOwnerReferenceId(), "PENDING")
        .stream()
        .mapToLong(LoanReturn::getQuantity)
        .sum();
  }

  private long remaining(Recall recall) {
    return recall.getQuantity() - recall.getFulfilledQuantity();
  }

  private long credit(Recall recall, Loan loan, long quantity) {
    long credited = Math.min(remaining(recall), quantity);
    if (credited == 0) return 0;
    recall.setFulfilledQuantity(recall.getFulfilledQuantity() + credited);
    if (remaining(recall) == 0) recall.setStatus("FULFILLED");
    else if (!clock.instant().isBefore(recall.getDueAt())) recall.setStatus("OVERDUE");
    else recall.setStatus("PARTIALLY_FULFILLED");
    publish(recall, loan);
    return credited;
  }

  private void publish(Recall recall, Loan loan) {
    events.confirm(
        found(connections.findById(loan.getConnectionId())),
        "RECALL_UPDATED",
        "RECALL_UPDATED",
        recall.getOwnerReferenceId(),
        Map.of(
            "loanId",
            loan.getBorrowerLoanId(),
            "externalId",
            "RECALL-" + recall.getOwnerReferenceId(),
            "quantity",
            recall.getQuantity(),
            "fulfilledQuantity",
            recall.getFulfilledQuantity(),
            "status",
            recall.getStatus(),
            "dueAt",
            recall.getDueAt().toString(),
            "reason",
            recall.getReason()));
  }
}
