package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;
import java.math.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only draft valuation. Loan submission independently locks and reserves every allocation. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoanCollateralPreviewService {

  private final BorrowerRepository db;
  private final MarketService market;
  private final LendingService lending;
  private final Clock clock;

  private String key(String type, Long security) {
    return "CASH".equals(type) ? "CASH" : "SECURITY:" + security;
  }

  private MarketPrice quote(long id) {
    var p = market.price(id);
    require(
      p.value().signum() > 0 && !p.at().isAfter(clock.instant()),
      "A valid market quote is required"
    );
    return p;
  }

  public Map<String, Object> preview(
    long org,
    LoanCollateralPreviewRequest request
  ) {
    var reservation = db.owned(Reservation.class, org, request.reservationId());
    var agreement = db.owned(Msla.class, org, n(reservation, "msla_id"));
    var errors = new ArrayList<String>();
    if (
      !"ACTIVE".equals(reservation.get("status")) ||
      !instant(reservation, "expires_at").isAfter(clock.instant())
    ) errors.add("Reservation is inactive or expired");
    if (request.quantity() > n(reservation, "remaining_quantity")) errors.add(
      "Loan quantity exceeds the remaining reservation"
    );
    LocalDate today = clock
      .instant()
      .atZone(ZoneId.of("Asia/Kolkata"))
      .toLocalDate();
    if (
      !"ACTIVE".equals(agreement.get("status")) ||
      today.isBefore(date(agreement, "effective_date")) ||
      today.isAfter(date(agreement, "expiry_date"))
    ) errors.add("MSLA is not active for this date");
    var accounts = db.rows(TraderAccount.class, eq("organizationId", org));
    BigDecimal cash = accounts.isEmpty()
      ? BigDecimal.ZERO
      : money(accounts.get(0), "available");
    var options = new LinkedHashMap<String, Map<String, Object>>();
    for (var rule : db.rows(
      CollateralHaircut.class,
      eq("mslaId", n(agreement, "id"))
    )) {
      String type = String.valueOf(rule.get("asset_type"));
      Long security = "CASH".equals(type) ? null : n(rule, "security_id");
      var option = new LinkedHashMap<String, Object>();
      option.put("key", key(type, security));
      option.put("assetType", type);
      option.put("securityId", security);
      option.put("haircut", money(rule, "haircut"));
      option.put(
        "symbol",
        "CASH".equals(type)
          ? "Cash"
          : db.row(SecurityMaster.class, security).get("symbol")
      );
      if (security == null) {
        option.put("unitPrice", BigDecimal.ONE);
        option.put("priceAt", null);
        option.put("available", cash);
      } else {
        var holdings = db.rows(
          Holding.class,
          eq("organizationId", org),
          eq("securityId", security)
        );
        BigDecimal available = holdings.isEmpty()
          ? BigDecimal.ZERO
          : BigDecimal.valueOf(n(holdings.get(0), "owned_quantity"))
              .subtract(
                BigDecimal.valueOf(n(holdings.get(0), "pledged_quantity"))
              )
              .subtract(BigDecimal.valueOf(n(holdings.get(0), "blocked_owned")))
              .max(BigDecimal.ZERO);
        option.put("available", available);
        try {
          var p = quote(security);
          option.put("unitPrice", p.value());
          option.put("priceAt", p.at());
        } catch (ResponseStatusException e) {
          option.put("unitPrice", null);
          option.put("priceAt", null);
        }
      }
      options.putIfAbsent(String.valueOf(option.get("key")), option);
    }
    BigDecimal loanValue = null,
      required = null;
    Instant priceAt = null;
    try {
      var p = quote(n(reservation, "security_id"));
      priceAt = p.at();
      loanValue = amount(
        p.value().multiply(BigDecimal.valueOf(request.quantity()))
      );
      required = amount(loanValue.multiply(money(agreement, "coverage_ratio")));
      if (
        lending
          .exposure(org, n(agreement, "id"))
          .add(loanValue)
          .compareTo(money(agreement, "credit_limit")) > 0
      ) errors.add("MSLA credit limit exceeded");
    } catch (ResponseStatusException e) {
      errors.add(
        "A valid loan security price is required to calculate coverage"
      );
    }
    var usage = new HashMap<String, BigDecimal>();
    for (var allocation : request.collateral()) {
      var units = "CASH".equals(allocation.assetType())
        ? allocation.cashAmount()
        : BigDecimal.valueOf(allocation.quantity());
      usage.merge(
        key(allocation.assetType(), allocation.securityId()),
        units,
        BigDecimal::add
      );
    }
    BigDecimal marketTotal = BigDecimal.ZERO,
      eligibleTotal = BigDecimal.ZERO;
    boolean known = true;
    var rows = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < request.collateral().size(); i++) {
      var allocation = request.collateral().get(i);
      boolean isCash = "CASH".equals(allocation.assetType());
      String assetKey = key(allocation.assetType(), allocation.securityId());
      var option = options.get(assetKey);
      var rowErrors = new ArrayList<String>();
      if (allocation.marginCallId() != null) rowErrors.add(
        "A new loan allocation cannot reference a margin call"
      );
      if (option == null) rowErrors.add(
        "Select collateral permitted by this MSLA"
      );
      if (
        isCash
          ? allocation.securityId() != null ||
              allocation.quantity() != 0 ||
              allocation.cashAmount().signum() <= 0
          : allocation.securityId() == null ||
              allocation.quantity() <= 0 ||
              allocation.cashAmount().signum() != 0
      ) rowErrors.add(
        "Enter a positive cash amount or whole security quantity"
      );
      BigDecimal value = null,
        recognized = null;
      if (option != null) {
        BigDecimal haircut = (BigDecimal) option.get("haircut"),
          unitPrice = (BigDecimal) option.get("unitPrice");
        if (
          usage.get(assetKey).compareTo((BigDecimal) option.get("available")) >
          0
        ) rowErrors.add(
          "Combined allocations exceed available " + option.get("symbol")
        );
        if (
          haircut.signum() < 0 || haircut.compareTo(BigDecimal.ONE) >= 0
        ) rowErrors.add("Invalid MSLA haircut");
        if (unitPrice == null) rowErrors.add(
          "Market price unavailable for " + option.get("symbol")
        );
        else {
          value = amount(
            (isCash
              ? allocation.cashAmount()
              : BigDecimal.valueOf(allocation.quantity())
            ).multiply(unitPrice)
          );
          recognized = amount(value.multiply(BigDecimal.ONE.subtract(haircut)));
        }
      }
      var row = new LinkedHashMap<String, Object>();
      row.put("index", i);
      row.put("assetType", allocation.assetType());
      row.put("securityId", allocation.securityId());
      row.put("unitPrice", option == null ? null : option.get("unitPrice"));
      row.put("haircut", option == null ? null : option.get("haircut"));
      row.put("available", option == null ? null : option.get("available"));
      row.put("marketValue", value);
      row.put("eligibleValue", recognized);
      row.put("errors", rowErrors);
      rows.add(row);
      if (value == null || recognized == null) known = false;
      else {
        marketTotal = marketTotal.add(value);
        eligibleTotal = eligibleTotal.add(recognized);
      }
      for (String message : rowErrors)
        errors.add("Collateral " + (i + 1) + ": " + message);
    }
    if (request.collateral().isEmpty()) errors.add(
      "Add at least one collateral allocation"
    );
    BigDecimal shortfall =
      required == null || !known
        ? null
        : amount(required.subtract(eligibleTotal).max(BigDecimal.ZERO));
    if (shortfall != null && shortfall.signum() > 0) errors.add(
      "Eligible collateral does not cover the loan requirement"
    );
    var result = new LinkedHashMap<String, Object>();
    result.put(
      "options",
      options
        .values()
        .stream()
        .sorted(Comparator.comparing(option -> option.get("key").toString()))
        .toList()
    );
    result.put("rows", rows);
    result.put("loanMarketValue", loanValue);
    result.put("requiredCollateral", required);
    result.put("coverageRatio", money(agreement, "coverage_ratio"));
    result.put("priceAt", priceAt);
    result.put("marketValue", known ? amount(marketTotal) : null);
    result.put("eligibleValue", known ? amount(eligibleTotal) : null);
    result.put("shortfall", shortfall);
    result.put(
      "surplus",
      required == null || !known
        ? null
        : amount(eligibleTotal.subtract(required).max(BigDecimal.ZERO))
    );
    result.put(
      "coveragePercent",
      loanValue == null || loanValue.signum() <= 0 || !known
        ? null
        : eligibleTotal
            .multiply(BigDecimal.valueOf(100))
            .divide(loanValue, 4, RoundingMode.HALF_UP)
    );
    result.put("eligible", errors.isEmpty());
    result.put("errors", errors);
    return result;
  }
}
