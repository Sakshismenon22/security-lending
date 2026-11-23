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

/** Internal demo sales of pledged collateral; proceeds stay encumbered until loan settlement. */
@Service
@RequiredArgsConstructor
@Transactional
public class CollateralLiquidationService {

  private final CollateralPositionRepository positions;
  private final CollateralLiquidationRepository sales;
  private final LoanDefaultRepository defaults;
  private final LoanRepository loans;
  private final LifecycleRequestRepository requests;
  private final LoanReturnRepository returns;
  private final SecurityMasterRepository securities;
  private final AssetRepository assets;
  private final LenderAccountRepository accounts;
  private final LenderFundingService funding;
  private final BorrowerConnectionRepository connections;
  private final CollateralService collateral;
  private final MarketService market;
  private final EventService events;
  private final TenantService tenants;
  private final JsonService json;
  private final Clock clock;

  public List<CollateralPosition> securityLots(long loan) {
    return positions
      .findByLoanId(loan)
      .stream()
      .filter(
        p ->
          "SECURITY".equals(p.getAssetType()) &&
          Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus()) &&
          p.getQuantity() > 0
      )
      .toList();
  }

  private String scope(LoanDefault def, Loan loan) {
    var lots = securityLots(loan.getId())
      .stream()
      .sorted(Comparator.comparing(CollateralPosition::getId))
      .map(
        p ->
          p.getId() +
          ":" +
          p.getSecurityId() +
          ":" +
          p.getQuantity() +
          ":" +
          p.getStatus()
      )
      .toList();
    return JsonService.hash(
      def.getOrganizationId() +
        "|" +
        def.getId() +
        "|" +
        def.getSourceVersion() +
        "|" +
        loan.getOutstandingQuantity() +
        "|" +
        lots
    );
  }

  public BigDecimal cash(long loan) {
    return positions
      .findByLoanId(loan)
      .stream()
      .filter(
        p ->
          "CASH".equals(p.getAssetType()) &&
          Set.of("POSTED", "RELEASE_REQUESTED").contains(p.getStatus())
      )
      .map(CollateralPosition::getCashAmount)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private BigDecimal price(long security) {
    var quote = market.price(security);
    require(
      quote.value().signum() > 0 && !quote.at().isAfter(clock.instant()),
      "Invalid collateral market price"
    );
    return quote.value();
  }

  @Transactional(noRollbackFor = org.springframework.web.server.ResponseStatusException.class)
  public BigDecimal requireCashFunding(
    long org,
    long loan,
    long quantity,
    BigDecimal unitPrice,
    BigDecimal fees
  ) {
    require(
      securityLots(loan).isEmpty(),
      "Sell securities collateral before placing the buy-in order"
    );
    BigDecimal shortfall = amount(unitPrice.multiply(BigDecimal.valueOf(quantity)).add(fees))
        .subtract(cash(loan)).max(BigDecimal.ZERO);
    require(shortfall.signum() == 0 || funding.availableFor(org, null).compareTo(shortfall) >= 0,
        "Insufficient cash collateral and available lender funds for this buy-in order");
    return shortfall;
  }

  public Map<String, Object> preview(long org, long defaultId) {
    tenants.exists(org);
    var def = found(defaults.findByIdAndOrganizationId(defaultId, org));
    var loan = found(loans.findByIdAndOrganizationId(def.getLoanId(), org));
    var lots = new ArrayList<Map<String, Object>>();
    var blockers = new ArrayList<String>();
    BigDecimal proceeds = BigDecimal.ZERO;
    for (var p : securityLots(loan.getId())) {
      var sec = found(securities.findById(p.getSecurityId()));
      var row = new LinkedHashMap<String, Object>();
      row.put("positionId", p.getId());
      row.put("securityId", sec.getId());
      row.put("symbol", sec.getSymbol());
      row.put("quantity", p.getQuantity());
      try {
        var quote = price(sec.getId());
        var value = amount(quote.multiply(BigDecimal.valueOf(p.getQuantity())));
        row.put("price", quote);
        row.put("proceeds", value);
        proceeds = proceeds.add(value);
      } catch (org.springframework.web.server.ResponseStatusException e) {
        row.put("price", null);
        row.put("proceeds", null);
        blockers.add("A valid market quote is required for " + sec.getSymbol());
      }
      if (!"POSTED".equals(p.getStatus())) blockers.add(
        "Resolve the pending collateral release for " + sec.getSymbol()
      );
      lots.add(row);
    }
    BigDecimal purchase = null;
    try {
      purchase = price(loan.getSecurityId()).multiply(
        BigDecimal.valueOf(loan.getOutstandingQuantity())
      );
    } catch (org.springframework.web.server.ResponseStatusException ignored) {}
    var result = new LinkedHashMap<String, Object>();
    result.put("saleScope", scope(def, loan));
    result.put("securityLots", lots);
    result.put("needsSale", !lots.isEmpty());
    result.put("cashHeld", cash(loan.getId()));
    result.put("lenderFundsAvailable", funding.availableFor(org, null));
    result.put("estimatedProceeds", blockers.isEmpty() ? proceeds : null);
    result.put("estimatedPurchaseCost", purchase);
    result.put(
      "canSell",
      !lots.isEmpty() &&
        blockers.isEmpty() &&
        "OPEN".equals(def.getStatus()) &&
        Set.of("RECALL", "MATURITY").contains(def.getObligationType()) &&
        Set.of("ACTIVE", "DEFAULTED").contains(loan.getStatus())
    );
    result.put("blockers", blockers);
    result.put("sales", sales.findByOrganizationIdAndLoanId(org, loan.getId()));
    return result;
  }

  public CollateralLiquidation sell(
    long org,
    long defaultId,
    String clientId,
    String saleScope
  ) {
    tenants.lock(org);
    var old = sales.findByOrganizationIdAndClientRequestId(org, clientId);
    if (old.isPresent()) {
      require(
        old.get().getDefaultId() == defaultId,
        "Client request ID belongs to another default"
      );
      require(
        Objects.equals(saleScope, old.get().getSaleScope()),
        "Client request ID reused with another collateral review"
      );
      return old.get();
    }
    var def = found(defaults.findByIdAndOrganizationId(defaultId, org));
    var loan = found(loans.findByIdAndOrganizationId(def.getLoanId(), org));
    require(
      "OPEN".equals(def.getStatus()) &&
        Set.of("RECALL", "MATURITY").contains(def.getObligationType()) &&
        Set.of("ACTIVE", "DEFAULTED").contains(loan.getStatus()) &&
        loan.getOutstandingQuantity() > 0,
      "An open securities default is required"
    );
    require(
      requests
        .findByOrganizationIdAndLoanIdIn(org, List.of(loan.getId()))
        .stream()
        .anyMatch(
          r ->
            "BUY_IN_REQUESTED".equals(r.getType()) &&
            Set.of("ACCEPTED", "COMPLETED").contains(r.getStatus()) &&
            json.tree(r.getPayload()).path("defaultReferenceId").asLong() ==
              def.getOwnerReferenceId()
        ),
      "An accepted buy-in request is required before selling collateral"
    );
    require(
      returns
        .findByLoanId(loan.getId())
        .stream()
        .noneMatch(r -> "PENDING".equals(r.getStatus())),
      "Resolve pending return requests first"
    );
    require(
      positions
        .findByLoanId(loan.getId())
        .stream()
        .noneMatch(p -> "PLEDGE_PENDING".equals(p.getStatus())),
      "Resolve pending collateral pledges first"
    );
    require(
      Objects.equals(saleScope, scope(def, loan)),
      "Collateral changed after review; refresh the sale preview before confirming"
    );
    var lots = securityLots(loan.getId());
    require(!lots.isEmpty(), "No securities collateral remains to sell");
    require(
      lots.stream().allMatch(p -> "POSTED".equals(p.getStatus())),
      "Resolve pending collateral releases before selling"
    );
    var cashAsset = found(assets.findByAssetName("CASH"));
    var records = new ArrayList<Map<String, Object>>();
    BigDecimal proceeds = BigDecimal.ZERO;
    String reference = "COLLATERAL-SALE-" + UUID.randomUUID();
    for (var p : lots) {
      long security = p.getSecurityId(),
        quantity = p.getQuantity();
      BigDecimal quote = price(security),
        value = amount(quote.multiply(BigDecimal.valueOf(quantity)));
      require(
        value.signum() > 0 && value.precision() <= 24,
        "Invalid sale proceeds"
      );
      var data = new LinkedHashMap<String, Object>();
      data.put("loanId", loan.getBorrowerLoanId());
      data.put("securityId", security);
      data.put("quantity", quantity);
      data.put("price", quote);
      data.put("quoteAt", market.price(security).at());
      data.put("proceeds", value);
      data.put("reference", reference);
      records.add(new LinkedHashMap<>(data));
      p.setAssetType("CASH");
      p.setAssetId(cashAsset.getId());
      p.setSecurityId(null);
      p.setQuantity(0L);
      p.setCashAmount(value);
      p.setHaircut(BigDecimal.ZERO);
      p.setSettlementReference(reference);
      // Conversion creates a cash-collateral opening movement for subsequent rebate accounting.
      collateral.movement(p, "PLEDGE_CONFIRMED", reference);
      events.confirm(
        found(connections.findById(p.getConnectionId())),
        "COLLATERAL_LIQUIDATED",
        "collateral_position",
        p.getBorrowerPositionId(),
        data
      );
      proceeds = proceeds.add(value);
    }
    require(
      proceeds.compareTo(new BigDecimal("999999999999999999.999999")) <= 0,
      "Sale proceeds exceed ledger limits"
    );
    var account = found(accounts.findByOrganizationId(org));
    account.setCashCollateralHeld(
      account.getCashCollateralHeld().add(proceeds)
    );
    var sale = new CollateralLiquidation();
    sale.setOrganizationId(org);
    sale.setLoanId(loan.getId());
    sale.setDefaultId(defaultId);
    sale.setClientRequestId(clientId);
    sale.setSaleScope(saleScope);
    sale.setReference(reference);
    sale.setProceeds(proceeds);
    sale.setLots(json.write(records));
    sale.setCreatedAt(clock.instant());
    sales.saveAndFlush(sale);
    events.local(
      org,
      "COLLATERAL_SECURITIES_SOLD",
      sale.getId(),
      Map.of("sale", sale, "venue", "INTERNAL_DEMO")
    );
    return sale;
  }
}
