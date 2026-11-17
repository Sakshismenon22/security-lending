package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReturnCancellationService {

  private final OrganizationRepository organizations;
  private final BorrowerConnectionRepository connections;
  private final LoanRepository loans;
  private final LoanReturnRepository returns;
  private final ReturnCancellationRepository cancellations;
  private final MslaRepository agreements;
  private final TenantService tenants;
  private final EventService events;
  private final JsonService json;

  /** Serialized with both receiving the original command and settling the lender return. */
  @Transactional
  public Object cancel(String body, String counterpartyId) {
    var command = json.parse(body, BorrowerCommand.class);
    require(
      "RETURN_REQUESTED".equals(command.type()) &&
        "COUNTERPARTY".equals(command.destination()),
      "Only return requests can be reconciled here"
    );
    require(
      counterpartyId != null && !counterpartyId.isBlank(),
      "Missing trusted counterparty"
    );
    var org = found(organizations.findByExternalId(command.lenderExternalId()));
    tenants.lock(org.getId());
    var connection = found(
      connections.findByOrganizationIdAndExternalIdAndBorrowerOrganizationId(
        org.getId(),
        counterpartyId,
        command.organizationId()
      )
    );
    require(connection.getActive(), "Borrower connection is inactive");
    var request = json.data(command.data(), ReturnCommand.class);
    var loan = found(
      loans.findByConnectionIdAndBorrowerLoanId(
        connection.getId(),
        request.loanId()
      )
    );
    var agreement = found(
      agreements.findByConnectionIdAndBorrowerMslaId(
        connection.getId(),
        command.mslaId()
      )
    );
    require(
      loan.getMslaId().equals(agreement.getId()),
      "Return agreement mismatch"
    );
    var old = cancellations.findByConnectionIdAndBorrowerReturnId(
      connection.getId(),
      command.referenceId()
    );
    if (old.isPresent()) {
      require(
        old.get().getEventId().equals(command.eventId()),
        "Return ID reused with another delivery event"
      );
      return json.tree(old.get().getConfirmation());
    }
    var current = returns.findByConnectionIdAndBorrowerReturnId(
      connection.getId(),
      command.referenceId()
    );
    boolean settled = false;
    if (current.isPresent()) {
      var returned = current.get();
      require(
        returned.getLoanId().equals(loan.getId()) &&
          returned.getQuantity() == request.quantity(),
        "Return quantity or loan mismatch"
      );
      settled = "SETTLED".equals(returned.getStatus());
      require(
        settled ||
          Set.of("PENDING", "REJECTED", "CANCELLED").contains(
            returned.getStatus()
          ),
        "Return cannot be reconciled in its current state"
      );
      if (!settled) returned.setStatus("CANCELLED");
    }
    var data = new LinkedHashMap<String, Object>();
    data.put(
      "reason",
      settled
        ? "Return was already settled before delivery failure reconciliation"
        : "Return cancelled after exhausted delivery attempts"
    );
    data.put("recoveryEventId", command.eventId());
    if (settled) data.put(
      "reference",
      Objects.requireNonNullElse(
        current.get().getSettlementReference(),
        "reconciled-settlement"
      )
    );
    var confirmation = events.confirm(
      connection,
      settled ? "RETURN_SETTLED" : "RETURN_REJECTED",
      "loan_return",
      command.referenceId(),
      data
    );
    var fence = new ReturnCancellation();
    fence.setEventId(command.eventId());
    fence.setOrganizationId(org.getId());
    fence.setConnectionId(connection.getId());
    fence.setBorrowerReturnId(command.referenceId());
    fence.setConfirmation(json.write(confirmation));
    cancellations.save(fence);
    events.audit(
      org.getId(),
      settled ? "FAILED_RETURN_ALREADY_SETTLED" : "FAILED_RETURN_CANCELLED",
      command.eventId()
    );
    return confirmation;
  }
}
