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

/** Securities purchases add owned stock; the original loan claim and collateral remain separate. */
@Service
@RequiredArgsConstructor
@Transactional
public class BuyinInventoryService {

  private final BuyInRepository buyins;
  private final LoanRepository loans;
  private final InventoryService inventory;
  private final LenderAccountLedgerRepository ledger;
  private final TenantService tenants;
  private final EventService events;
  private final Clock clock;

  public BuyIn receive(long org, long id, String reference) {
    tenants.lock(org);
    var report = found(buyins.findByIdAndOrganizationId(id, org));
    if (report.getInventoryReceivedAt() != null) {
      require(
        reference.equals(report.getInventoryReference()),
        "Buy-in inventory receipt already has another reference"
      );
      return report;
    }
    require(
      buyins
        .findByOrganizationIdAndInventoryReference(org, reference)
        .isEmpty(),
      "Inventory reference already used for another trade"
    );
    require(
      Set.of("EXECUTED", "COMPLETED", "SETTLED").contains(report.getStatus()),
      "Only executed buy-ins can be received"
    );
    require(
      report.getQuantity() > 0 && report.getPrice().signum() > 0,
      "Invalid executed trade"
    );
    var loan = found(loans.findByIdAndOrganizationId(report.getLoanId(), org));
    var position = inventory.position(org, loan.getSecurityId());
    position.setTotalQuantity(
      Math.addExact(position.getTotalQuantity(), report.getQuantity())
    );
    report.setInventoryReceivedAt(clock.instant());
    report.setInventoryReference(reference);
    inventory.movement(
      position,
      "BUY_IN_SECURITIES_RECEIVED",
      report.getQuantity(),
      reference
    );
    BigDecimal amount = report
      .getPrice()
      .multiply(BigDecimal.valueOf(report.getQuantity()))
      .add(report.getCosts());
    require(
      amount.compareTo(new BigDecimal("999999999999999999.999999")) <= 0,
      "Buy-in purchase amount is too large"
    );
    var entry = new LenderAccountLedger();
    entry.setOrganizationId(org);
    entry.setLoanId(loan.getId());
    entry.setType("BUY_IN_PURCHASE_PAYABLE");
    entry.setAmount(amount);
    entry.setReference(reference);
    entry.setCreatedAt(clock.instant());
    ledger.save(entry);
    events.local(
      org,
      "BUY_IN_INVENTORY_BOOKED",
      report.getId(),
      Map.of(
        "buyinId",
        report.getId(),
        "loanId",
        loan.getId(),
        "securityId",
        loan.getSecurityId(),
        "quantity",
        report.getQuantity(),
        "price",
        report.getPrice(),
        "costs",
        report.getCosts(),
        "purchasePayable",
        amount,
        "reference",
        reference
      )
    );
    return report;
  }
}
