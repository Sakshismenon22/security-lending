package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.AllocationRequest;
import com.example.borrower_service.dto.CollateralReleaseRequest;
import java.math.RoundingMode;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Service
@Transactional
public class CollateralService {
    private final BorrowerRepository db;
    private final MarketService market;
    private final DirectoryService accounts;
    private final EventWriter events;

    public CollateralService(
            BorrowerRepository db,
            MarketService market,
            DirectoryService accounts,
            EventWriter events) {
        this.db = db;
        this.market = market;
        this.accounts = accounts;
        this.events = events;
    }

    public Map<String, Object> post(long org, long loanId, AllocationRequest r) {
        db.lock(org);
        var loan = db.owned(Loan.class, org, loanId);
        require(
                Set.of("PENDING_SETTLEMENT", "ACTIVE", "RETURN_PENDING")
                        .contains(loan.get("status")),
                "Collateral cannot be added in this loan state");
        if (r.marginCallId() != null) {
            var call = db.owned(MarginCall.class, org, r.marginCallId());
            require(
                    n(call, "loan_id") == loanId
                            && Set.of("OPEN", "PARTIALLY_FULFILLED", "OVERDUE")
                                    .contains(call.get("status")),
                    "Margin call does not belong to this loan or is already resolved");
        }
        boolean cash = "CASH".equals(r.assetType());
        require(
                cash
                        ? (r.securityId() == null
                                && r.quantity() == 0
                                && r.cashAmount().signum() > 0)
                        : (r.securityId() != null
                                && r.quantity() > 0
                                && r.cashAmount().signum() == 0),
                "Cash requires an amount only; securities require securityId and quantity only");
        var rules =
                cash
                        ? db.rows(
                                CollateralHaircut.class,
                                eq("mslaId", n(loan, "msla_id")),
                                eq("assetType", "CASH"))
                        : db.rows(
                                CollateralHaircut.class,
                                eq("mslaId", n(loan, "msla_id")),
                                eq("assetType", "SECURITY"),
                                eq("securityId", r.securityId()));
        require(!rules.isEmpty(), "Collateral is not eligible under the MSLA");
        if (cash) {
            accounts.changeCash(
                    org, r.cashAmount().negate(), "COLLATERAL_PLEDGED", String.valueOf(loanId));
            db.editMatching(
                    TraderAccount.class,
                    entity -> {
                        entity.setPledged(entity.getPledged().add(r.cashAmount()));
                    },
                    eq("organizationId", org));
        } else {
            market.price(r.securityId());
            db.holding(org, r.securityId());
            var h =
                    db.matchingRow(
                            Holding.class,
                            eq("organizationId", org),
                            eq("securityId", r.securityId()));
            require(
                    n(h, "owned_quantity") - n(h, "pledged_quantity") - n(h, "blocked_owned")
                            >= r.quantity(),
                    "Insufficient owned securities available for collateral");
            db.editMatching(
                    Holding.class,
                    entity -> {
                        entity.setPledgedQuantity(entity.getPledgedQuantity() + (r.quantity()));
                    },
                    eq("organizationId", org),
                    eq("securityId", r.securityId()));
        }
        long asset = n(db.matchingRow(Asset.class, eq("assetName", r.assetType())), "id");
        long id =
                db.create(
                        CollateralPosition::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setLoanId(loanId);
                            entity.setAssetType(r.assetType());
                            entity.setAssetId(asset);
                            entity.setSecurityId(r.securityId());
                            entity.setQuantity(r.quantity());
                            entity.setCashAmount(r.cashAmount());
                            entity.setHaircut(money(rules.get(0), "haircut"));
                            entity.setStatus("PLEDGE_PENDING");
                            entity.setMarginCallId(r.marginCallId());
                        });
        movement(
                org,
                loanId,
                id,
                "PLEDGE_REQUESTED",
                r.cashAmount(),
                r.quantity(),
                asset,
                r.securityId());
        events.write(
                org,
                "COLLATERAL_PLEDGE_REQUESTED",
                loanId,
                Map.of("positionId", id, "allocation", r));
        return db.owned(CollateralPosition.class, org, id);
    }

    public Map<String, Object> respondToMarginCall(long org, long callId, AllocationRequest r) {
        db.lock(org);
        var call = db.owned(MarginCall.class, org, callId);
        require(
                r.marginCallId() == null || r.marginCallId() == callId,
                "Margin call reference does not match the request path");
        return post(
                org,
                n(call, "loan_id"),
                new AllocationRequest(
                        r.assetType(), r.securityId(), r.quantity(), r.cashAmount(), callId));
    }

    void applyPledgeConfirmation(long org, long id) {
        var p = db.owned(CollateralPosition.class, org, id);
        require("PLEDGE_PENDING".equals(p.get("status")), "Collateral pledge is not pending");
        db.edit(
                CollateralPosition.class,
                id,
                entity -> {
                    entity.setStatus("POSTED");
                });
        movement(
                org,
                n(p, "loan_id"),
                id,
                "PLEDGE_CONFIRMED",
                money(p, "cash_amount"),
                n(p, "quantity"),
                n(p, "asset_id"),
                (Long) p.get("security_id"));
    }

    /**
     * Submission preview only. End-of-day valuation and margin decisions come from the lifecycle
     * service.
     */
    public Map<String, Object> preview(long org, long loanId) {
        var loan = db.owned(Loan.class, org, loanId);
        var p = market.price(n(loan, "security_id"));
        BigDecimal loanValue =
                amount(p.value().multiply(BigDecimal.valueOf(n(loan, "outstanding_quantity"))));
        BigDecimal required = amount(loanValue.multiply(money(loan, "coverage_ratio"))),
                recognized = BigDecimal.ZERO,
                marketValue = BigDecimal.ZERO;
        var positions = new ArrayList<Map<String, Object>>();
        for (var position :
                db.rows(
                        CollateralPosition.class,
                        eq("organizationId", org),
                        eq("loanId", loanId),
                        oneOf("status", "PLEDGE_PENDING", "POSTED", "RELEASE_REQUESTED"))) {
            BigDecimal value =
                    "CASH".equals(position.get("asset_type"))
                            ? money(position, "cash_amount")
                            : market.price(n(position, "security_id"))
                                    .value()
                                    .multiply(BigDecimal.valueOf(n(position, "quantity")));
            BigDecimal recognizedValue =
                    amount(value.multiply(BigDecimal.ONE.subtract(money(position, "haircut"))));
            marketValue = marketValue.add(value);
            recognized = recognized.add(recognizedValue);
            var row = new LinkedHashMap<>(position);
            row.put("market_value", amount(value));
            row.put("recognized_value", recognizedValue);
            positions.add(row);
        }
        return Map.of(
                "indicative",
                true,
                "loanId",
                loanId,
                "loanMarketValue",
                loanValue,
                "requiredCollateral",
                required,
                "marketValue",
                amount(marketValue),
                "recognizedValue",
                amount(recognized),
                "shortfall",
                amount(required.subtract(recognized).max(BigDecimal.ZERO)),
                "surplus",
                amount(recognized.subtract(required).max(BigDecimal.ZERO)),
                "priceAt",
                p.at(),
                "positions",
                positions);
    }

    public void validateInitialAllocation(long org, long loanId) {
        require(
                ((BigDecimal) preview(org, loanId).get("shortfall")).signum() == 0,
                "Insufficient proposed haircut-adjusted collateral");
    }

    /** Current confirmed coverage, less amounts already requested from other lots. */
    public Map<String, Object> releasePreview(long org, long loanId) {
      var loan = db.owned(Loan.class, org, loanId);
      boolean allowed = Set.of("ACTIVE", "RETURN_PENDING", "CLOSED").contains(
        loan.get("status")
      );
      BigDecimal required =
        n(loan, "outstanding_quantity") == 0
          ? BigDecimal.ZERO
          : amount(
              market
                .price(n(loan, "security_id"))
                .value()
                .multiply(BigDecimal.valueOf(n(loan, "outstanding_quantity")))
                .multiply(money(loan, "coverage_ratio"))
            );
      BigDecimal recognized = BigDecimal.ZERO,
        reserved = BigDecimal.ZERO;
      var positions = new ArrayList<Map<String, Object>>();
      for (var p : db.rows(
        CollateralPosition.class,
        eq("organizationId", org),
        eq("loanId", loanId),
        oneOf("status", "POSTED", "RELEASE_REQUESTED")
      )) {
        boolean cash = "CASH".equals(p.get("asset_type"));
        BigDecimal price = cash
          ? BigDecimal.ONE
          : market.price(n(p, "security_id")).value();
        BigDecimal factor = BigDecimal.ONE.subtract(money(p, "haircut"));
        require(
          factor.signum() >= 0 &&
            factor.compareTo(BigDecimal.ONE) <= 0 &&
            price.signum() > 0,
          "Invalid collateral price or haircut"
        );
        BigDecimal units = cash
          ? money(p, "cash_amount")
          : BigDecimal.valueOf(n(p, "quantity"));
        recognized = recognized.add(
          amount(units.multiply(price).multiply(factor))
        );
        if ("RELEASE_REQUESTED".equals(p.get("status"))) {
          BigDecimal requested = cash
            ? p.get("requested_release_cash") == null
              ? units
              : money(p, "requested_release_cash")
            : BigDecimal.valueOf(
                p.get("requested_release_quantity") == null
                  ? n(p, "quantity")
                  : n(p, "requested_release_quantity")
              );
          reserved = reserved.add(
            amount(requested.multiply(price).multiply(factor))
          );
        }
        var row = new LinkedHashMap<String, Object>(p);
        row.put("unitRecognizedValue", price.multiply(factor));
        if (!cash) {
          var security = db.row(SecurityMaster.class, n(p, "security_id"));
          row.put("securitySymbol", security.get("symbol"));
          row.put("securityName", security.get("name"));
          row.put("exchange", security.get("exchange"));
        }
        positions.add(row);
      }
      BigDecimal excess = recognized
        .subtract(required)
        .subtract(reserved)
        .max(BigDecimal.ZERO);
      for (var p : positions) {
        boolean cash = "CASH".equals(p.get("asset_type"));
        BigDecimal units = cash
          ? money(p, "cash_amount")
          : BigDecimal.valueOf(n(p, "quantity"));
        BigDecimal max =
          allowed &&
          "POSTED".equals(p.get("status")) &&
          recognized.subtract(required).subtract(reserved).signum() >= 0
            ? ((BigDecimal) p.get("unitRecognizedValue")).signum() == 0
              ? units
              : excess
                  .divide(
                    (BigDecimal) p.get("unitRecognizedValue"),
                    cash ? 6 : 0,
                    RoundingMode.DOWN
                  )
                  .min(units)
            : BigDecimal.ZERO;
        p.put("maxCashAmount", cash ? max : BigDecimal.ZERO);
        p.put("maxQuantity", cash ? 0L : max.longValueExact());
      }
      return Map.of(
        "loanId",
        loanId,
        "requiredCollateral",
        required,
        "recognizedValue",
        recognized,
        "pendingReleaseValue",
        reserved,
        "availableExcess",
        allowed ? excess : BigDecimal.ZERO,
        "positions",
        positions
      );
    }

    public Map<String, Object> requestRelease(long org, long id) {
      return requestRelease(org, id, null);
    }

    public Map<String, Object> requestRelease(
      long org,
      long id,
      CollateralReleaseRequest request
    ) {
      db.lock(org);
      var p = db.owned(CollateralPosition.class, org, id);
      var loan = db.owned(Loan.class, org, n(p, "loan_id"));
      require(
        "POSTED".equals(p.get("status")),
        "Collateral release is already pending or completed"
      );
      require(
        Set.of("ACTIVE", "RETURN_PENDING", "CLOSED").contains(loan.get("status")),
        "Collateral cannot be released in this loan state"
      );
      boolean cash = "CASH".equals(p.get("asset_type"));
      boolean full =
        request == null ||
        (request.cashAmount() == null && request.quantity() == null);
      BigDecimal requestedCash = cash
        ? full
          ? money(p, "cash_amount")
          : request.cashAmount()
        : BigDecimal.ZERO;
      long requestedQty = cash
        ? 0L
        : full
          ? n(p, "quantity")
          : request.quantity() == null
            ? 0L
            : request.quantity();
      require(
        cash
          ? requestedCash != null &&
              requestedCash.signum() > 0 &&
              (full || request.quantity() == null)
          : requestedQty > 0 && (full || request.cashAmount() == null),
        "Specify a cash amount for cash collateral or whole units for securities"
      );
      var preview = releasePreview(org, n(p, "loan_id"));
      @SuppressWarnings("unchecked")
      var positions = (List<Map<String, Object>>) preview.get("positions");
      var limit = positions
        .stream()
        .filter(x -> n(x, "id") == id)
        .findFirst()
        .orElseThrow();
      require(
        cash
          ? requestedCash.scale() <= 6 &&
              requestedCash.compareTo((BigDecimal) limit.get("maxCashAmount")) <=
                0
          : requestedQty <= (Long) limit.get("maxQuantity"),
        "Requested release exceeds available excess collateral"
      );
      db.edit(CollateralPosition.class, id, entity -> {
        entity.setStatus("RELEASE_REQUESTED");
        entity.setReleaseRequestStatus("PENDING");
        entity.setReleaseRejectionReason(null);
        entity.setRequestedReleaseCash(requestedCash);
        entity.setRequestedReleaseQuantity(requestedQty);
      });
      movement(
        org,
        n(p, "loan_id"),
        id,
        "RELEASE_REQUESTED",
        requestedCash,
        requestedQty,
        n(p, "asset_id"),
        (Long) p.get("security_id")
      );
      events.write(
        org,
        "COLLATERAL_RELEASE_REQUESTED",
        id,
        Map.of(
          "loanId",
          n(p, "loan_id"),
          "cashAmount",
          requestedCash,
          "quantity",
          requestedQty
        )
      );
      return db.owned(CollateralPosition.class, org, id);
    }

    void applyReleaseConfirmation(long org, long id, boolean rejectedLoan) {
      applyReleaseConfirmation(org, id, rejectedLoan, null, null);
    }

    void applyReleaseConfirmation(
      long org,
      long id,
      boolean rejectedLoan,
      BigDecimal releasedCash,
      Long releasedQuantity
    ) {
      var p = db.owned(CollateralPosition.class, org, id);
      long loanId = n(p, "loan_id");
      require(
        rejectedLoan
          ? "PLEDGE_PENDING".equals(p.get("status"))
          : // A full return lets the lender release collateral immediately. Its
            // confirmation can therefore arrive before a borrower release request.
            Set.of("RELEASE_REQUESTED", "POSTED").contains(p.get("status")),
        "No pending pledge or release request exists"
      );
      require(!"RELEASED".equals(p.get("status")), "Collateral already released");
      BigDecimal cash =
        releasedCash == null ? money(p, "cash_amount") : releasedCash;
      long qty = releasedQuantity == null ? n(p, "quantity") : releasedQuantity;
      require(
        cash.signum() >= 0 &&
          cash.compareTo(money(p, "cash_amount")) <= 0 &&
          qty >= 0 &&
          qty <= n(p, "quantity"),
        "Release exceeds held collateral"
      );
      boolean full = "CASH".equals(p.get("asset_type"))
        ? cash.compareTo(money(p, "cash_amount")) == 0
        : qty == n(p, "quantity");
      db.edit(CollateralPosition.class, id, entity -> {
        entity.setStatus(
          rejectedLoan ? "REJECTED" : full ? "RELEASED" : "POSTED"
        );
        if (!full) {
          entity.setCashAmount(entity.getCashAmount().subtract(cash));
          entity.setQuantity(entity.getQuantity() - qty);
        }
        if (!rejectedLoan) {
          entity.setReleaseRequestStatus("COMPLETED");
          entity.setReleaseRejectionReason(null);
        }
        entity.setRequestedReleaseCash(null);
        entity.setRequestedReleaseQuantity(null);
      });
      if ("CASH".equals(p.get("asset_type"))) {
        db.editMatching(
          TraderAccount.class,
          entity -> {
            entity.setPledged(entity.getPledged().subtract(cash));
          },
          eq("organizationId", org)
        );
        accounts.changeCash(org, cash, "COLLATERAL_RELEASED", String.valueOf(id));
      } else db.editMatching(
        Holding.class,
        entity -> {
          entity.setPledgedQuantity(entity.getPledgedQuantity() - qty);
        },
        eq("organizationId", org),
        eq("securityId", n(p, "security_id"))
      );
      movement(
        org,
        loanId,
        id,
        rejectedLoan ? "PLEDGE_REJECTED" : "RELEASE_CONFIRMED",
        cash,
        qty,
        n(p, "asset_id"),
        (Long) p.get("security_id")
      );
      events.write(
        org,
        "COLLATERAL_RELEASE_CONFIRMED",
        id,
        Map.of("loanId", loanId)
      );
    }

    private void movement(
            long org,
            long loan,
            long position,
            String type,
            BigDecimal cash,
            long qty,
            long asset,
            Long security) {
        db.create(
                CollateralHistory::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setLoanId(loan);
                    entity.setPositionId(position);
                    entity.setType(type);
                    entity.setAmount(cash);
                    entity.setQuantity(qty);
                    entity.setAssetId(asset);
                    entity.setSecurityId(security);
                });
    }
}
