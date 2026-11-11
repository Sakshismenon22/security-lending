package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LifecycleRequestService {
  private final LoanRepository loans;
  private final MslaRepository agreements;
  private final RebateRepository rebates;
  private final LoanDefaultRepository defaults;
  private final RecallService recalls;
  private final LifecycleRequestRepository requests;
  private final TenantService tenants;
  private final EventService events;
  private final JsonService json;
  private final Clock clock;

  public LifecycleRequest recall(long org, long loanId, RecallRequest r) {
    tenants.lock(org);
    var loan = found(loans.findByIdAndOrganizationId(loanId, org));
    var retry = requests.findByOrganizationIdAndClientRequestId(org, r.clientRequestId());
    if (retry.isPresent())
      return same(retry.get(), "RECALL_REQUESTED", json.write(r), loan.getId());
    require("ACTIVE".equals(loan.getStatus()), "Recall requires an active loan");
    require(
        found(agreements.findById(loan.getMslaId())).getRecallAllowed(),
        "MSLA does not permit recall");
    long open = recalls.outstanding(loanId);
    long requested =
        requests.findByOrganizationId(org, Pageable.unpaged()).getContent().stream()
            .filter(
                x ->
                    Objects.equals(x.getLoanId(), loanId)
                        && x.getType().equals("RECALL_REQUESTED")
                        && Set.of("PENDING", "ACCEPTED").contains(x.getStatus()))
            .mapToLong(x -> json.parse(x.getPayload(), RecallRequest.class).quantity())
            .sum();
    require(
        r.quantity() <= loan.getOutstandingQuantity() - open - requested,
        "Recall exceeds uncommitted outstanding quantity");
    return create(loan, "RECALL_REQUESTED", r.clientRequestId(), r);
  }

  public LifecycleRequest rebatePayment(long org, long id, LifecycleActionRequest r) {
    tenants.lock(org);
    var rebate = found(rebates.findByIdAndOrganizationId(id, org));
    require(rebate.getSettlementFrequency() == null, "This rebate settles automatically under its MSLA");
    require("ACCRUED".equals(rebate.getStatus()), "Rebate is not awaiting payment");
    var loan = found(loans.findById(rebate.getLoanId()));
    return create(
        loan,
        "REBATE_PAYMENT_REQUESTED",
        r.clientRequestId(),
        Map.of("rebateReferenceId", rebate.getOwnerReferenceId(), "amount", rebate.getAmount()));
  }

  public LifecycleRequest buyIn(long org, long id, LifecycleActionRequest r) {
    tenants.lock(org);
    var def = found(defaults.findByIdAndOrganizationId(id, org));
    require("OPEN".equals(def.getStatus()), "Default is not open");
    return create(
        found(loans.findById(def.getLoanId())),
        "BUY_IN_REQUESTED",
        r.clientRequestId(),
        Map.of("defaultReferenceId", def.getOwnerReferenceId()));
  }

  private LifecycleRequest same(LifecycleRequest previous, String type, String data, Long loanId) {
    require(
        Objects.equals(previous.getLoanId(), loanId)
            && type.equals(previous.getType())
            && json.tree(data).equals(json.tree(previous.getPayload())),
        "Client request ID reused for different details");
    return previous;
  }

  private LifecycleRequest create(Loan loan, String type, String clientId, Object data) {
    String payload = json.write(data);
    var old = requests.findByOrganizationIdAndClientRequestId(loan.getOrganizationId(), clientId);
    if (old.isPresent()) return same(old.get(), type, payload, loan.getId());
    var r = new LifecycleRequest();
    r.setOrganizationId(loan.getOrganizationId());
    r.setConnectionId(loan.getConnectionId());
    r.setLoanId(loan.getId());
    r.setType(type);
    r.setClientRequestId(clientId);
    r.setPayload(payload);
    r.setStatus("PENDING");
    r.setCreatedAt(clock.instant());
    requests.saveAndFlush(r);
    events.lifecycle(
        loan.getOrganizationId(),
        type,
        r.getId(),
        Map.of("loanId", loan.getId(), "connectionId", loan.getConnectionId(), "request", data));
    return r;
  }
}
