package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.found;
import static com.example.lender_service.service.Checks.require;

import com.example.lender_service.dto.SettlementRequest;
import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarginCallService {
  private final MarginCallRepository calls;
  private final LoanRepository loans;
  private final BorrowerConnectionRepository connections;
  private final EventService events;
  private final CollateralPositionRepository positions;
  private final MarketService market;
  private final TenantService tenants;

  @Transactional
  public Map<String, Object> confirm(long org, long id, SettlementRequest request) {
    tenants.lock(org);
    var call = found(calls.findByIdAndOrganizationId(id, org));
    var loan = found(loans.findByIdAndOrganizationId(call.getLoanId(), org));
    publish(call, loan, request.reference());
    return Map.of(
        "status", "CONFIRMED",
        "marginCallId", id,
        "loanId", loan.getBorrowerLoanId(),
        "reference", request.reference());
  }

  /** Called with the lender organization locked, in the collateral settlement transaction. */
  void applyCollateralReceipt(CollateralPosition position, Loan loan) {
    if (position.getMarginCallId() == null) return;
    // The borrower stores the owner reference as its margin-call ID.
    var call = found(calls.findByOrganizationIdAndOwnerReferenceId(
        position.getOrganizationId(), position.getMarginCallId()));
    require(
        Objects.equals(call.getLoanId(), loan.getId())
            && Objects.equals(call.getConnectionId(), position.getConnectionId()),
        "Margin call does not belong to this loan and borrower connection");
    if (!Set.of("PENDING", "OPEN", "PARTIALLY_FULFILLED", "OVERDUE").contains(call.getStatus())
        || position.getCreatedAt().isBefore(call.getCreatedAt())) return;

    // Reconcile posted positions so retries and receipts recorded before this change
    // count once. Earlier cycles on the same loan are excluded.
    BigDecimal recognized = positions.findByLoanId(loan.getId()).stream()
        .filter(p -> Objects.equals(p.getMarginCallId(), call.getOwnerReferenceId())
            && Objects.equals(p.getConnectionId(), call.getConnectionId())
            && Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus())
            && !p.getCreatedAt().isBefore(call.getCreatedAt()))
        .map(this::recognizedValue)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal fulfilled = recognized.max(call.getFulfilledAmount()).min(call.getAmount());
    if (fulfilled.signum() == 0) return;
    String status = fulfilled.compareTo(call.getAmount()) == 0 ? "FULFILLED" : "PARTIALLY_FULFILLED";
    if (fulfilled.compareTo(call.getFulfilledAmount()) == 0 && status.equals(call.getStatus())) return;
    call.setFulfilledAmount(fulfilled);
    call.setStatus(status);
    publish(call, loan, position.getSettlementReference());
  }

  private BigDecimal recognizedValue(CollateralPosition position) {
    BigDecimal value =
        "CASH".equals(position.getAssetType())
            ? position.getCashAmount()
            : market.price(position.getSecurityId()).value()
                .multiply(BigDecimal.valueOf(position.getQuantity()));
    return Checks.amount(value.multiply(BigDecimal.ONE.subtract(position.getHaircut())));
  }

  private void publish(MarginCall call, Loan loan, String reference) {
    var connection = found(connections.findById(loan.getConnectionId()));
    var data = new LinkedHashMap<String, Object>();
    data.put("loanId", loan.getBorrowerLoanId());
    data.put("externalId", String.valueOf(call.getOwnerReferenceId()));
    data.put("amount", call.getAmount().toPlainString());
    data.put("fulfilledAmount", call.getFulfilledAmount().toPlainString());
    data.put("status", call.getStatus());
    data.put("dueAt", call.getDueAt().toString());
    data.put("reason", call.getReason());
    data.put("reference", reference);

    events.confirm(
        connection,
        "MARGIN_CALL_UPDATED",
        "MARGIN_CALL_UPDATED",
        call.getOwnerReferenceId(),
        data);
  }
}
