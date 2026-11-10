package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CollateralService {
  private final CollateralPositionRepository collateral;
  private final CollateralHistoryRepository history;
  private final LoanRepository loans;
  private final MslaRepository agreements;
  private final BorrowerConnectionRepository connections;
  private final LenderAccountRepository accounts;
  private final TenantService tenants;
  private final MarketService market;
  private final EventService events;
  private final Clock clock;
  private final LenderAccountLedgerRepository ledger;
  private final MarginCallService marginCalls;

  public Map<String, Object> preview(
      long org, long loanId, boolean includePending, Long excludingPosition) {
    var loan = found(loans.findByIdAndOrganizationId(loanId, org));
    var agreement = found(agreements.findById(loan.getMslaId()));
    var price = market.price(loan.getSecurityId());
    BigDecimal
        loanValue =
            amount(price.value().multiply(BigDecimal.valueOf(loan.getOutstandingQuantity()))),
        recognized = BigDecimal.ZERO;
    var positions = new ArrayList<Map<String, Object>>();
    for (var p : collateral.findByLoanId(loanId)) {
      if (Objects.equals(excludingPosition, p.getId())
          || !(Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus())
              || includePending && "PLEDGE_PENDING".equals(p.getStatus()))) continue;
      BigDecimal value =
          "CASH".equals(p.getAssetType())
              ? p.getCashAmount()
              : market
                  .price(p.getSecurityId())
                  .value()
                  .multiply(BigDecimal.valueOf(p.getQuantity()));
      BigDecimal net = amount(value.multiply(BigDecimal.ONE.subtract(p.getHaircut())));
      recognized = recognized.add(net);
      positions.add(Map.of("position", p, "marketValue", amount(value), "recognizedValue", net));
    }
    BigDecimal required = amount(loanValue.multiply(agreement.getCoverageRatio()));
    return Map.of(
        "indicative",
        true,
        "loanId",
        loanId,
        "loanMarketValue",
        loanValue,
        "requiredCollateral",
        required,
        "recognizedValue",
        amount(recognized),
        "shortfall",
        amount(required.subtract(recognized).max(BigDecimal.ZERO)),
        "positions",
        positions,
        "priceAt",
        price.at());
  }

  public void requireInitialCoverage(long org, long loanId) {
    require(
        ((BigDecimal) preview(org, loanId, false, null).get("shortfall")).signum() == 0,
        "Confirmed collateral does not cover the loan at settlement");
  }

  public CollateralPosition confirm(long org, long id, String reference) {
    tenants.lock(org);
    var p = found(collateral.findByIdAndOrganizationId(id, org));
    if ("POSTED".equals(p.getStatus())) {
      require(
          reference.equals(p.getSettlementReference()),
          "Collateral already confirmed with a different reference");
      if (p.getMarginCallId() != null) {
        marginCalls.applyCollateralReceipt(p, found(loans.findById(p.getLoanId())));
      }
      return p;
    }
    require("PLEDGE_PENDING".equals(p.getStatus()), "Collateral is not pending");
    require(p.getLoanId() != null, "Loan request must exist before confirming collateral");
    var l = found(loans.findById(p.getLoanId()));
    require(
        Set.of("PENDING_REVIEW", "APPROVED_PENDING_SETTLEMENT", "ACTIVE").contains(l.getStatus()),
        "Loan does not accept collateral");
    p.setStatus("POSTED");
    p.setSettlementReference(reference);
    if ("CASH".equals(p.getAssetType())) {
      var a = found(accounts.findByOrganizationId(org));
      a.setCashCollateralHeld(a.getCashCollateralHeld().add(p.getCashAmount()));
    }
    movement(p, "PLEDGE_CONFIRMED", reference);
    events.confirm(
        found(connections.findById(p.getConnectionId())),
        "COLLATERAL_PLEDGED",
        "collateral_position",
        p.getBorrowerPositionId(),
        Map.of());
    marginCalls.applyCollateralReceipt(p, l);
    events.local(org, "COLLATERAL_SETTLED", id, Map.of("position", p, "loan", l));
    return p;
  }

  public CollateralPosition reject(long org, long id, String reason) {
    tenants.lock(org);
    var p = found(collateral.findByIdAndOrganizationId(id, org));
    require("PLEDGE_PENDING".equals(p.getStatus()), "Collateral is not pending");
    p.setStatus("REJECTED");
    movement(p, "PLEDGE_REJECTED", reason);
    events.confirm(
        found(connections.findById(p.getConnectionId())),
        "COLLATERAL_REJECTED",
        "collateral_position",
        p.getBorrowerPositionId(),
        Map.of("reason", reason));
    return p;
  }

  public CollateralPosition release(long org, long id, String reference, boolean approve) {
    tenants.lock(org);
    var p = found(collateral.findByIdAndOrganizationId(id, org));
    require("RELEASE_REQUESTED".equals(p.getStatus()), "No borrower release request exists");
    if (approve) {
      var l = found(loans.findById(p.getLoanId()));
      require(
          !Set.of("DEFAULTED", "RECOVERED").contains(l.getStatus()),
          "Recovery collateral release belongs to the lifecycle owner");
      if (l.getOutstandingQuantity() > 0 && "ACTIVE".equals(l.getStatus()))
        require(
            ((BigDecimal) preview(org, l.getId(), false, id).get("shortfall")).signum() == 0,
            "Release would leave insufficient collateral");
      p.setStatus("RELEASED");
      p.setSettlementReference(reference);
      if ("CASH".equals(p.getAssetType())) {
        var a = found(accounts.findByOrganizationId(org));
        a.setCashCollateralHeld(a.getCashCollateralHeld().subtract(p.getCashAmount()));
      }
      movement(p, "RELEASE_CONFIRMED", reference);
    } else p.setStatus("POSTED");
    events.confirm(
        found(connections.findById(p.getConnectionId())),
        approve ? "COLLATERAL_RELEASED" : "COLLATERAL_RELEASE_REJECTED",
        "collateral_position",
        p.getBorrowerPositionId(),
        Map.of("reference", reference));
    events.local(org, "COLLATERAL_RELEASE_DECIDED", id, Map.of("position", p));
    return p;
  }

  public void movement(CollateralPosition p, String type, String reference) {
    var h = new CollateralHistory();
    h.setOrganizationId(p.getOrganizationId());
    h.setPositionId(p.getId());
    h.setType(type);
    h.setReference(reference.length() > 160 ? reference.substring(0, 160) : reference);
    h.setCreatedAt(clock.instant());
    history.save(h);
    if ("CASH".equals(p.getAssetType())
        && Set.of("PLEDGE_CONFIRMED", "RELEASE_CONFIRMED").contains(type)) {
      var entry = new LenderAccountLedger();
      entry.setOrganizationId(p.getOrganizationId());
      entry.setType(type);
      entry.setAmount(
          type.equals("PLEDGE_CONFIRMED") ? p.getCashAmount() : p.getCashAmount().negate());
      entry.setReference(h.getReference());
      entry.setCreatedAt(clock.instant());
      ledger.save(entry);
    }
  }
}
