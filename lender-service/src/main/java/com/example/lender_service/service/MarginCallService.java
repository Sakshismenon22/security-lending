package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.found;

import com.example.lender_service.dto.SettlementRequest;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MarginCallService {
  private final MarginCallRepository calls;
  private final LoanRepository loans;
  private final BorrowerConnectionRepository connections;
  private final EventService events;

  public Map<String, Object> confirm(long org, long id, SettlementRequest request) {
    var call = found(calls.findByIdAndOrganizationId(id, org));
    var loan = found(loans.findByIdAndOrganizationId(call.getLoanId(), org));
    var connection = found(connections.findById(loan.getConnectionId()));

    var data = new LinkedHashMap<String, Object>();
    data.put("loanId", loan.getBorrowerLoanId());
    data.put("externalId", String.valueOf(call.getOwnerReferenceId()));
    data.put("amount", call.getAmount().toPlainString());
    data.put("fulfilledAmount", call.getFulfilledAmount().toPlainString());
    data.put("status", call.getStatus());
    data.put("dueAt", call.getDueAt().toString());
    data.put("reason", call.getReason());
    data.put("reference", request.reference());

    events.confirm(
        connection,
        "MARGIN_CALL_UPDATED",
        "MARGIN_CALL_UPDATED",
        call.getOwnerReferenceId(),
        data);

    return Map.of(
        "status", "CONFIRMED",
        "marginCallId", id,
        "loanId", loan.getBorrowerLoanId(),
        "reference", request.reference());
  }
}
