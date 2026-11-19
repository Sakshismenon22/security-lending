package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.math.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BuyinLoanCloseoutService {

  private final LoanDefaultRepository defaults;
  private final LoanRepository loans;
  private final BuyInRepository reports;
  private final BuyinLoanCloseoutRepository closeouts;
  private final CollateralPositionRepository positions;
  private final LoanReturnRepository returns;
  private final BuyinOrderRepository orders;
  private final LifecycleRequestRepository requests;
  private final BorrowerConnectionRepository connections;
  private final LenderAccountLedgerRepository ledger;
  private final RecallRepository recalls;
  private final MarginCallRepository margins;
  private final InventoryService inventory;
  private final CollateralService collateral;
  private final TenantService tenants;
  private final EventService events;
  private final Clock clock;

  private List<BuyIn> receipts(long org, long loan) {
    return reports
      .findByOrganizationId(org, Pageable.unpaged())
      .stream()
      .filter(
        b ->
          b.getLoanId() == loan &&
          b.getInventoryReceivedAt() != null &&
          Set.of("EXECUTED", "COMPLETED", "SETTLED").contains(b.getStatus()) &&
          b.getQuantity() > b.getCloseoutQuantity()
      )
      .sorted(Comparator.comparing(BuyIn::getId))
      .toList();
  }

  private BigDecimal cost(BuyIn b, long q) {
    return b
      .getPrice()
      .multiply(BigDecimal.valueOf(q))
      .add(
        b
          .getCosts()
          .multiply(BigDecimal.valueOf(q))
          .divide(BigDecimal.valueOf(b.getQuantity()), 6, RoundingMode.HALF_UP)
      );
  }

  public Map<String, Object> preview(long org, long defaultId) {
    tenants.exists(org);
    var def = found(defaults.findByIdAndOrganizationId(defaultId, org));
    var loan = found(loans.findByIdAndOrganizationId(def.getLoanId(), org));
    var result = new LinkedHashMap<String, Object>();
    var existing = closeouts.findByOrganizationIdAndLoanId(org, loan.getId());
    long available = receipts(org, loan.getId())
        .stream()
        .mapToLong(b -> b.getQuantity() - b.getCloseoutQuantity())
        .sum(),
      left = loan.getOutstandingQuantity();
    BigDecimal cost = BigDecimal.ZERO;
    for (var b : receipts(org, loan.getId())) {
      long q = Math.min(left, b.getQuantity() - b.getCloseoutQuantity());
      cost = cost.add(cost(b, q));
      left -= q;
      if (left == 0) break;
    }
    var lots = positions.findByLoanId(loan.getId());
    BigDecimal cash = lots
      .stream()
      .filter(
        p ->
          "CASH".equals(p.getAssetType()) &&
          Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus())
      )
      .map(CollateralPosition::getCashAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    var blockers = new ArrayList<String>();
    if (
      !Set.of("ACTIVE", "DEFAULTED").contains(loan.getStatus()) ||
      loan.getOutstandingQuantity() <= 0
    ) blockers.add("Loan is not open");
    if (available < loan.getOutstandingQuantity()) blockers.add(
      "Received buy-in quantity must cover the full outstanding loan"
    );
    if (cash.compareTo(cost) < 0) blockers.add(
      "Insufficient cash collateral to settle buy-in costs; securities liquidation is required separately"
    );
    if (
      lots.stream().anyMatch(p -> "PLEDGE_PENDING".equals(p.getStatus()))
    ) blockers.add("Resolve pending collateral pledges first");
    if (
      returns
        .findByLoanId(loan.getId())
        .stream()
        .anyMatch(r -> "PENDING".equals(r.getStatus()))
    ) blockers.add("Resolve pending return requests first");
    if (
      orders
        .findByOrganizationId(org)
        .stream()
        .anyMatch(
          o ->
            o.getLoanId().equals(loan.getId()) &&
            Set.of("PENDING", "EXECUTION_PENDING").contains(o.getStatus())
        )
    ) blockers.add("Resolve pending buy-in orders first");
    if (
      requests
        .findByOrganizationIdAndLoanIdIn(org, List.of(loan.getId()))
        .stream()
        .anyMatch(
          r ->
            Set.of(
              "BUY_IN_REQUESTED",
              "BUY_IN_EXECUTION_REQUESTED",
              "MARGIN_CALL_REQUESTED",
              "RECALL_REQUESTED"
            ).contains(r.getType()) &&
            Set.of("PENDING", "ACCEPTED").contains(r.getStatus())
        )
    ) blockers.add("Resolve pending buy-in or obligation requests first");
    result.put("loanId", loan.getId());
    result.put("loanStatus", loan.getStatus());
    result.put("outstandingQuantity", loan.getOutstandingQuantity());
    result.put("receivedQuantity", available);
    result.put("purchaseCost", cost);
    result.put("cashCollateralHeld", cash);
    result.put(
      "cashCollateralToReturn",
      cash.subtract(cost).max(BigDecimal.ZERO)
    );
    result.put("eligible", blockers.isEmpty() && existing.isEmpty());
    result.put("blockers", blockers);
    result.put("closeout", existing.orElse(null));
    if (existing.isPresent()) {
      result.put("purchaseCost", existing.get().getPurchaseCost());
      result.put("receivedQuantity", available + existing.get().getQuantity());
      result.put(
        "cashCollateralToReturn",
        existing.get().getCashCollateralReturned()
      );
    }
    return result;
  }

  public BuyinLoanCloseout close(long org, long defaultId, String reference) {
    tenants.lock(org);
    var def = found(defaults.findByIdAndOrganizationId(defaultId, org));
    var loan = found(loans.findByIdAndOrganizationId(def.getLoanId(), org));
    var old = closeouts.findByOrganizationIdAndLoanId(org, loan.getId());
    if (old.isPresent()) {
      require(
        old.get().getReference().equals(reference),
        "Loan already closed with another reference"
      );
      return old.get();
    }
    require(
      closeouts.findByOrganizationIdAndReference(org, reference).isEmpty(),
      "Closeout reference already used"
    );
    var check = preview(org, defaultId);
    require(
      Boolean.TRUE.equals(check.get("eligible")),
      String.join("; ", (List<String>) check.get("blockers"))
    );
    long quantity = loan.getOutstandingQuantity(),
      left = quantity;
    BigDecimal cost = (BigDecimal) check.get("purchaseCost"),
      value = BigDecimal.ZERO;
    for (var b : receipts(org, loan.getId())) {
      long q = Math.min(left, b.getQuantity() - b.getCloseoutQuantity());
      if (q == 0) break;
      value = value.add(b.getPrice().multiply(BigDecimal.valueOf(q)));
      b.setCloseoutQuantity(b.getCloseoutQuantity() + q);
      left -= q;
    }
    var stock = inventory.position(org, loan.getSecurityId());
    require(
      stock.getLoanedQuantity() >= quantity &&
        stock.getTotalQuantity() >= quantity,
      "Loan inventory is inconsistent"
    );
    stock.setTotalQuantity(stock.getTotalQuantity() - quantity);
    stock.setLoanedQuantity(stock.getLoanedQuantity() - quantity);
    inventory.movement(stock, "BUY_IN_CLAIM_WRITTEN_OFF", -quantity, reference);
    BigDecimal remaining = cost;
    for (var p : positions.findByLoanId(loan.getId()))
      if (
        "CASH".equals(p.getAssetType()) &&
        Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus()) &&
        remaining.signum() > 0
      ) {
        BigDecimal applied = p.getCashAmount().min(remaining);
        if (applied.signum() > 0) {
          collateral.applyToBuyin(org, p.getId(), applied, reference);
          remaining = remaining.subtract(applied);
        }
      }
    loan.setOutstandingQuantity(0L);
    loan.setStatus("CLOSED");
    loan.setClosedAt(clock.instant());
    for (var p : positions.findByLoanId(loan.getId()))
      if (Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus())) {
        p.setStatus("RELEASE_REQUESTED");
        collateral.release(org, p.getId(), reference, true);
      }
    var journal = new LenderAccountLedger();
    journal.setOrganizationId(org);
    journal.setLoanId(loan.getId());
    journal.setType("BUY_IN_PAYABLE_SETTLED");
    journal.setAmount(cost.negate());
    journal.setReference(reference);
    journal.setCreatedAt(clock.instant());
    ledger.save(journal);
    var closeout = new BuyinLoanCloseout();
    closeout.setOrganizationId(org);
    closeout.setLoanId(loan.getId());
    closeout.setDefaultId(defaultId);
    closeout.setQuantity(quantity);
    closeout.setPurchaseCost(cost);
    closeout.setCashCollateralApplied(cost);
    closeout.setCashCollateralReturned(
      (BigDecimal) check.get("cashCollateralToReturn")
    );
    closeout.setReference(reference);
    closeout.setCreatedAt(loan.getClosedAt());
    closeouts.saveAndFlush(closeout);
    var connection = found(connections.findById(loan.getConnectionId()));
    events.confirm(
      connection,
      "LOAN_STATUS_UPDATED",
      "loan",
      loan.getBorrowerLoanId(),
      Map.of(
        "status",
        "CLOSED",
        "outstandingQuantity",
        0,
        "buyinCloseout",
        true,
        "closedAt",
        loan.getClosedAt().toString(),
        "closedQuantity",
        quantity,
        "buyinPrice",
        value.divide(BigDecimal.valueOf(quantity), 6, RoundingMode.HALF_UP),
        "reference",
        reference
      )
    );
    publishSummary(org,closeout.getId());
    resolveObligations(org, loan, connection);
    events.lifecycle(
      org,
      "LOAN_CLOSED",
      loan.getId(),
      Map.of("loanId", loan.getId(), "reason", "BUY_IN_CLOSEOUT")
    );
    events.local(
      org,
      "BUY_IN_LOAN_CLOSED",
      loan.getId(),
      Map.of("closeout", closeout, "loan", loan)
    );
    return closeout;
  }

  public List<BuyinLoanCloseout> pendingSummaries() {
    return closeouts.findTop100ByBorrowerSummaryQueuedFalseOrderByIdAsc();
  }

  public void publishSummary(long org, long id) {
    tenants.lock(org);
    var closeout = found(closeouts.findById(id));
    require(
      closeout.getOrganizationId() == org,
      "Closeout belongs to another organization"
    );
    if (closeout.isBorrowerSummaryQueued()) return;
    var loan = found(
      loans.findByIdAndOrganizationId(closeout.getLoanId(), org)
    );
    require("CLOSED".equals(loan.getStatus()), "Loan is not closed");
    BigDecimal purchaseValue = reports
      .findByOrganizationId(org, Pageable.unpaged())
      .stream()
      .filter(
        b -> b.getLoanId().equals(loan.getId()) && b.getCloseoutQuantity() > 0
      )
      .map(b ->
        b.getPrice().multiply(BigDecimal.valueOf(b.getCloseoutQuantity()))
      )
      .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal executionCosts = closeout
      .getPurchaseCost()
      .subtract(purchaseValue);
    require(
      executionCosts.signum() >= 0,
      "Closeout purchase allocation is inconsistent"
    );
    var data = new LinkedHashMap<String, Object>();
    data.put("loanId", loan.getBorrowerLoanId());
    data.put("quantity", closeout.getQuantity());
    data.put("purchaseValue", purchaseValue);
    data.put("executionCosts", executionCosts);
    data.put("purchaseCost", closeout.getPurchaseCost());
    data.put("cashCollateralApplied", closeout.getCashCollateralApplied());
    data.put("cashCollateralReturned", closeout.getCashCollateralReturned());
    data.put(
      "averagePrice",
      purchaseValue.divide(
        BigDecimal.valueOf(closeout.getQuantity()),
        6,
        RoundingMode.HALF_UP
      )
    );
    data.put("closedAt", closeout.getCreatedAt().toString());
    data.put("reference", closeout.getReference());
    events.confirm(
      found(connections.findById(loan.getConnectionId())),
      "BUY_IN_CLOSEOUT_REPORTED",
      "buyin_closure_summary",
      closeout.getId(),
      data
    );
    closeout.setBorrowerSummaryQueued(true);
  }

  private void resolveObligations(
    long org,
    Loan loan,
    BorrowerConnection connection
  ) {
    for (var r : recalls.findByLoanIdAndStatusInOrderByDueAtAscIdAsc(
      loan.getId(),
      Set.of("OPEN", "PARTIALLY_FULFILLED", "OVERDUE")
    )) {
      r.setStatus("CANCELLED");
      events.confirm(
        connection,
        "RECALL_UPDATED",
        "recall",
        r.getOwnerReferenceId(),
        Map.of(
          "loanId",
          loan.getBorrowerLoanId(),
          "quantity",
          r.getQuantity(),
          "fulfilledQuantity",
          r.getFulfilledQuantity(),
          "status",
          "CANCELLED",
          "reason",
          r.getReason(),
          "dueAt",
          r.getDueAt().toString()
        )
      );
    }
    for (var m : margins.findByLoanId(loan.getId()))
      if (
        Set.of("OPEN", "PENDING", "PARTIALLY_FULFILLED", "OVERDUE").contains(
          m.getStatus()
        )
      ) {
        m.setStatus("CANCELLED");
        events.confirm(
          connection,
          "MARGIN_CALL_UPDATED",
          "margin_call",
          m.getOwnerReferenceId(),
          Map.of(
            "loanId",
            loan.getBorrowerLoanId(),
            "externalId",
            "MARGIN-" + m.getOwnerReferenceId(),
            "amount",
            m.getAmount(),
            "fulfilledAmount",
            m.getFulfilledAmount(),
            "status",
            "CANCELLED",
            "reason",
            m.getReason(),
            "dueAt",
            m.getDueAt().toString()
          )
        );
      }
    for (var d : defaults.findByOrganizationId(org, Pageable.unpaged()))
      if (d.getLoanId().equals(loan.getId())) d.setStatus("RESOLVED");
  }
}
