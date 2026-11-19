package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.BorrowerEvent;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import jakarta.validation.Validator;
import org.springframework.data.domain.Sort;

import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

/**
 * Applies authoritative confirmations to borrower state. It never approves, calculates, schedules,
 * or executes lender actions.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class BorrowerEventProjectionService {
    private static final Logger log = LoggerFactory.getLogger(BorrowerEventProjectionService.class);
    private final BorrowerRepository db;
    private final CollateralService collateral;
    private final DirectoryService accounts;
    private final PaymentService payments;
    private final EventWriter events;
    private final Validator validator;
    private final TradingService trading;

    public Map<String, Object> apply(BorrowerEvent event) {
        log.info(
                "Applying borrower confirmation: type={}, organizationId={}, referenceId={},"
                    + " version={}",
                event.type(),
                event.organizationId(),
                event.referenceId(),
                event.version());
        require(validator.validate(event).isEmpty(), "Invalid confirmation envelope");
        long org = event.organizationId(), id = event.referenceId();
        db.lock(org);
        if (!db.rows(InboxEvent.class, eq("eventId", event.eventId())).isEmpty())
            return Map.of("duplicate", true);
        String resource =
                resource(event.type())
                        + (event.type().equals("LENDER_REFERENCE_UPDATED") ? "" : ":" + org);
        // Some projections share a resource version, e.g. reservation confirmation and expiry.
        var cursor =
                db.lockedRows(
                        ProjectionCursor.class, eq("resource", resource), eq("referenceId", id));
        if (!cursor.isEmpty() && n(cursor.get(0), "version") >= event.version()) {
            db.persist(
                    InboxEvent::new,
                    entity -> {
                        entity.setEventId(event.eventId());
                    });
            return Map.of("stale", true);
        }
        var d = event.data();
        switch (event.type()) {
            case "LENDER_REFERENCE_UPDATED" -> lender(id, d);
            case "AGREEMENT_OFFERED" -> agreementOffer(org, id, d);
            case "AGREEMENT_ACCEPTED", "AGREEMENT_REJECTED" -> {
                var a = db.owned(Msla.class, org, id);
                require(
                        Set.of("OFFERED", "ACCEPTANCE_PENDING", "REJECTION_PENDING", "PROPOSED")
                                .contains(a.get("status")),
                        "Agreement is not awaiting a decision");
                db.edit(
                        Msla.class,
                        id,
                        entity -> {
                            entity.setStatus(
                                    event.type().equals("AGREEMENT_ACCEPTED")
                                            ? "ACTIVE"
                                            : "REJECTED");
                        });
            }
            case "LOCATE_APPROVED", "LOCATE_REJECTED", "LOCATE_EXPIRED" ->
                    locate(org, id, event.type(), d);
            case "RESERVATION_CONFIRMED",
                            "RESERVATION_REJECTED",
                            "RESERVATION_RELEASED",
                            "RESERVATION_EXPIRED",
                            "RESERVATION_UPDATED" ->
                    reservation(org, id, event.type(), d);
            case "LOAN_SETTLED" -> settled(org, id, event.occurredAt());
            case "LOAN_REJECTED" -> loanRejected(org, id);
            case "RETURN_SETTLED", "RETURN_REJECTED" ->
                    returned(org, id, event.type().equals("RETURN_SETTLED"));
            case "COLLATERAL_LIQUIDATED" -> collateral.applyLiquidation(org,id,d);
            case "COLLATERAL_PLEDGED" -> collateral.applyPledgeConfirmation(org, id);
            case "COLLATERAL_REJECTED" -> collateral.applyReleaseConfirmation(org, id, true);
            case "COLLATERAL_APPLIED" -> collateralApplied(org,id,d);
            case "COLLATERAL_RELEASED" -> collateral.applyReleaseConfirmation(
                    org, id, false,
                    d.get("cashAmount") != null ? decimal(d, "cashAmount") : null,
                    d.get("quantity") != null ? Long.valueOf(d.get("quantity").toString()) : null);
            case "COLLATERAL_RELEASE_REJECTED" -> {
                var p = db.owned(CollateralPosition.class, org, id);
                require(
                        "RELEASE_REQUESTED".equals(p.get("status")),
                        "Collateral release is not pending");
                db.edit(
                        CollateralPosition.class,
                        id,
                        entity -> {
                            entity.setStatus("POSTED");
                            entity.setReleaseRequestStatus("REJECTED");
                            String reason = String.valueOf(
                                    d.getOrDefault("reason", d.getOrDefault("reference", "Rejected by lender")));
                            entity.setReleaseRejectionReason(
                                    reason.substring(0, Math.min(1000, reason.length())));
                            entity.setRequestedReleaseCash(null);
                            entity.setRequestedReleaseQuantity(null);
                        });
            }
            case "PAYMENT_CONFIRMED", "PAYMENT_REJECTED" ->
                    payments.applyConfirmation(
                            org,
                            id,
                            event.type().equals("PAYMENT_CONFIRMED"),
                            decimal(d, "amount"));
            case "FEE_BILL_ISSUED" -> fee(org, id, d);
            case "ACCRUAL_REPORTED" -> accrual(event);
            case "REBATE_ACCRUED" -> rebate(org, id, d);
            case "REBATE_PAID" -> rebatePaid(org, id, d, event.occurredAt());
            case "VALUATION_REPORTED" -> valuation(event);
            case "MARGIN_CALL_UPDATED" -> margin(org, id, d);
            case "RECALL_UPDATED" -> recall(org, id, d);
            case "DEFAULT_REPORTED" -> defaultView(org, id, d);
            case "BUY_IN_CLOSEOUT_REPORTED" -> buyinClosureSummary(org,d);
            case "BUY_IN_REPORTED" -> buyInView(org, id, d);
            case "LOAN_STATUS_UPDATED" -> {
                var loan = db.owned(Loan.class, org, id);
                if(Boolean.TRUE.equals(d.get("buyinCloseout"))) {buyinClosed(org,id,d,event.occurredAt());break;}
                String status = text(d, "status");
                require(
                        Set.of("DEFAULTED", "RECOVERED", "CLOSED").contains(status),
                        "Unsupported externally controlled loan status");
                long outstanding = number(d, "outstandingQuantity");
                require(
                        outstanding >= 0 && outstanding <= n(loan, "outstanding_quantity"),
                        "Invalid outstanding quantity");
                db.edit(
                        Loan.class,
                        id,
                        entity -> {
                            entity.setStatus(status);
                            entity.setOutstandingQuantity(outstanding);
                        });
            }
            case "HOLDING_IMPORTED" -> {
                db.row(SecurityMaster.class, id);
                db.holding(org, id);
                long owned = number(d, "ownedQuantity"), borrowed = number(d, "borrowedQuantity");
                require(owned >= 0 && borrowed >= 0, "Invalid holding quantities");
                db.editMatching(
                        Holding.class,
                        entity -> {
                            entity.setOwnedQuantity(owned);
                            entity.setBorrowedQuantity(borrowed);
                            entity.setAverageCost(decimal(d, "averageCost"));
                        },
                        eq("organizationId", org),
                        eq("securityId", id));
            }
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported borrower confirmation " + event.type());
        }
        if (cursor.isEmpty())
            db.persist(
                    ProjectionCursor::new,
                    entity -> {
                        entity.setResource(resource);
                        entity.setReferenceId(id);
                        entity.setVersion(event.version());
                    });
        else
            db.editMatching(
                    ProjectionCursor.class,
                    entity -> {
                        entity.setVersion(event.version());
                    },
                    eq("resource", resource),
                    eq("referenceId", id));
        db.persist(
                InboxEvent::new,
                entity -> {
                    entity.setEventId(event.eventId());
                });
        events.write(
                org,
                "BORROWER_VIEW_UPDATED",
                id,
                Map.of("sourceEventId", event.eventId(), "sourceType", event.type()));
        if (Set.of(
                        "MARGIN_CALL_UPDATED",
                        "RECALL_UPDATED",
                        "DEFAULT_REPORTED",
                        "BUY_IN_REPORTED",
                        "BUY_IN_CLOSEOUT_REPORTED",
                        "LOAN_SETTLED",
                        "RETURN_SETTLED",
                        "REBATE_PAID",
                        "COLLATERAL_RELEASED",
                        "COLLATERAL_APPLIED",
                        "COLLATERAL_LIQUIDATED",
                        "LOAN_STATUS_UPDATED",
                        "COLLATERAL_RELEASE_REJECTED")
                .contains(event.type()))
            db.create(
                    Notification::new,
                    entity -> {
                        entity.setOrganizationId(org);
                        entity.setType(event.type());
                        entity.setReference(String.valueOf(event.type().equals("BUY_IN_CLOSEOUT_REPORTED")?number(d,"loanId"):id));
                        entity.setMessage(event.type().equals("BUY_IN_CLOSEOUT_REPORTED")?"Loan closed through buy-in":event.type().replace('_', ' '));
                    });
        return Map.of("applied", true);
    }

    private String resource(String type) {
        if (type.equals("AGREEMENT_OFFERED")) return "msla_offer";
        if (type.startsWith("AGREEMENT_")) return "msla";
        if (type.startsWith("LOCATE_")) return "locate_request";
        if (type.startsWith("RESERVATION_")) return "reservation";
        if (type.startsWith("LOAN_")) return "loan";
        if (type.startsWith("RETURN_")) return "loan_return";
        if (type.startsWith("COLLATERAL_")) return "collateral_position";
        if (type.startsWith("PAYMENT_")) return "payment_request";
        if (type.startsWith("REBATE_")) return "rebate";
        return type;
    }

    private void lender(long id, Map<String, Object> d) {
        if (db.rows(LenderReference.class, eq("id", id)).isEmpty())
            db.persist(
                    LenderReference::new,
                    entity -> {
                        entity.setId(id);
                        entity.setName(text(d, "name"));
                        entity.setExternalId(text(d, "externalId"));
                    });
        else
            db.edit(
                    LenderReference.class,
                    id,
                    entity -> {
                        entity.setName(text(d, "name"));
                        entity.setExternalId(text(d, "externalId"));
                    });
    }

    @SuppressWarnings("unchecked")
    private void agreementOffer(long org, long lenderAgreementId, Map<String, Object> d) {
        long lenderId = number(d, "lenderReferenceId");
        db.row(LenderReference.class, lenderId);
        require(
                db.rows(
                                Msla.class,
                                eq("organizationId", org),
                                eq("lenderId", lenderId),
                                eq("lenderMslaId", lenderAgreementId))
                        .isEmpty(),
                "Agreement offer already exists");
        require(
                !dateValue(d, "expiryDate").isBefore(dateValue(d, "effectiveDate")),
                "Agreement expires before its effective date");
        int dayBasis = (int) number(d, "dayBasis");
        require(dayBasis == 360 || dayBasis == 365, "Invalid agreement day basis");
        Object rawRules = d.get("collateralRules");
        require(rawRules instanceof List<?> && !((List<?>) rawRules).isEmpty(), "Missing collateral rules");
        long agreementId =
                db.create(
                        Msla::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setLenderId(lenderId);
                            entity.setLenderMslaId(lenderAgreementId);
                            entity.setNumber(text(d, "number"));
                            entity.setStatus("OFFERED");
                            entity.setEffectiveDate(dateValue(d, "effectiveDate"));
                            entity.setExpiryDate(dateValue(d, "expiryDate"));
                            entity.setCreditLimit(decimal(d, "creditLimit"));
                            entity.setFeeRate(decimal(d, "feeRate"));
                            entity.setRebateRate(decimal(d, "rebateRate"));
                            entity.setCoverageRatio(decimal(d, "coverageRatio"));
                            entity.setBillingFrequency(text(d, "billingFrequency"));
                            entity.setRebateSettlementFrequency(text(d, "rebateSettlementFrequency"));
                            entity.setDayBasis(dayBasis);
                            entity.setRecallAllowed(Boolean.parseBoolean(text(d, "recallAllowed")));
                            entity.setMarginHours((int) number(d, "marginHours"));
                            entity.setRecallHours((int) number(d, "recallHours"));
                        });
        var seen = new HashSet<String>();
        for (Object rawRule : (List<?>) rawRules) {
            require(rawRule instanceof Map<?, ?>, "Invalid collateral rule");
            Map<String, Object> rule = (Map<String, Object>) rawRule;
            String assetType = text(rule, "assetType");
            Object rawSecurity = rule.get("securityId");
            Long securityId = rawSecurity == null ? null : number(rule, "securityId");
            BigDecimal haircut = decimal(rule, "haircut");
            boolean cash = "CASH".equals(assetType);
            require(haircut.signum() >= 0 && haircut.compareTo(new BigDecimal("0.99999999")) <= 0, "Collateral haircut must be in [0,1)");
            require(
                    (cash && securityId == null && haircut.signum() == 0)
                            || (!cash && "SECURITY".equals(assetType) && securityId != null),
                    "Invalid collateral eligibility rule");
            require(seen.add(assetType + ":" + securityId), "Duplicate collateral rule");
            if (!cash) db.row(SecurityMaster.class, securityId);
            long assetId = n(db.matchingRow(Asset.class, eq("assetName", assetType)), "id");
            db.create(
                    CollateralHaircut::new,
                    entity -> {
                        entity.setMslaId(agreementId);
                        entity.setAssetType(assetType);
                        entity.setSecurityId(securityId);
                        entity.setHaircut(haircut);
                        entity.setAssetId(assetId);
                    });
        }
    }

    private void locate(long org, long id, String type, Map<String, Object> d) {
        var l = db.owned(LocateRequest.class, org, id);
        if (type.equals("LOCATE_APPROVED")) {
            require("PENDING".equals(l.get("status")), "Locate is not pending");
            long quantity = number(d, "approvedQuantity");
            require(quantity > 0 && quantity <= n(l, "quantity"), "Invalid approved quantity");
            db.edit(
                    LocateRequest.class,
                    id,
                    entity -> {
                        entity.setStatus(
                                quantity == n(l, "quantity") ? "APPROVED" : "PARTIALLY_APPROVED");
                        entity.setApprovedQuantity(quantity);
                    });
        } else
            db.edit(
                    LocateRequest.class,
                    id,
                    entity -> {
                        entity.setStatus(type.equals("LOCATE_REJECTED") ? "REJECTED" : "EXPIRED");
                    });
    }

    private void reservation(long org, long id, String type, Map<String, Object> d) {
        var r = db.owned(Reservation.class, org, id);
        if (type.equals("RESERVATION_UPDATED")) {
            long remaining = number(d, "remainingQuantity");
            String status = text(d, "status");
            require(
                    remaining >= 0
                            && remaining <= n(r, "quantity")
                            && Set.of("ACTIVE", "CONSUMED", "EXPIRED", "RELEASED").contains(status),
                    "Invalid authoritative reservation state");
            db.edit(
                    Reservation.class,
                    id,
                    entity -> {
                        entity.setRemainingQuantity(remaining);
                        entity.setStatus(status);
                    });
            return;
        }
        if (type.equals("RESERVATION_CONFIRMED")) {
            require(
                    "REQUESTED".equals(r.get("status")),
                    "Reservation is not awaiting confirmation");
            long quantity = number(d, "quantity");
            require(quantity > 0 && quantity <= n(r, "quantity"), "Invalid reserved quantity");
            db.edit(
                    Reservation.class,
                    id,
                    entity -> {
                        entity.setQuantity(quantity);
                        entity.setRemainingQuantity(quantity);
                        entity.setStatus("ACTIVE");
                    });
        } else {
            String status =
                    type.equals("RESERVATION_RELEASED")
                            ? "RELEASED"
                            : type.equals("RESERVATION_EXPIRED") ? "EXPIRED" : "REJECTED";
            db.edit(
                    Reservation.class,
                    id,
                    entity -> {
                        entity.setStatus(status);
                        entity.setRemainingQuantity(0L);
                    });
        }
    }

    private void buyinClosureSummary(long org, Map<String, Object> d) {
      long loanId = number(d, "loanId");
      var loan = db.owned(Loan.class, org, loanId);
      require(
        "CLOSED".equals(loan.get("status")) &&
          n(loan, "outstanding_quantity") == 0,
        "Closure summary requires a closed loan"
      );
      long quantity = number(d, "quantity");
      BigDecimal value = decimal(d, "purchaseValue"),
        costs = decimal(d, "executionCosts"),
        total = decimal(d, "purchaseCost"),
        applied = decimal(d, "cashCollateralApplied"),
        returned = decimal(d, "cashCollateralReturned"),
        price = decimal(d, "averagePrice");
      String reference = text(d, "reference");
      require(
        quantity > 0 &&
          quantity <= n(loan, "quantity") &&
          value.signum() > 0 &&
          costs.signum() >= 0 &&
          total.compareTo(value.add(costs)) == 0 &&
          applied.compareTo(total) == 0 &&
          returned.signum() >= 0 &&
          price.signum() > 0 &&
          reference.length() <= 120,
        "Invalid buy-in closure summary"
      );
      var existing = db.entities(
        BuyinClosureSummary.class,
        Sort.unsorted(),
        1,
        eq("organizationId", org),
        eq("loanId", loanId)
      );
      if (!existing.isEmpty()) {
        var old = existing.get(0);
        require(
          old.getReference().equals(reference) &&
            old.getQuantity() == quantity &&
            old.getPurchaseValue().compareTo(value) == 0 &&
            old.getExecutionCosts().compareTo(costs) == 0 &&
            old.getCashCollateralApplied().compareTo(applied) == 0 &&
            old.getCashCollateralReturned().compareTo(returned) == 0,
          "Closure summary cannot change settled amounts"
        );
        return;
      }
      db.create(BuyinClosureSummary::new, e -> {
        e.setOrganizationId(org);
        e.setLoanId(loanId);
        e.setQuantity(quantity);
        e.setPurchaseValue(value);
        e.setExecutionCosts(costs);
        e.setPurchaseCost(total);
        e.setCashCollateralApplied(applied);
        e.setCashCollateralReturned(returned);
        e.setAveragePrice(price);
        e.setReference(reference);
        e.setClosedAt(instantValue(d, "closedAt"));
      });
      if (costs.signum() > 0) {
        // The fee is already included in collateral applied to the buy-in. Record its
        // allocation without debiting available cash or duplicating the purchase cost.
        var account = db.matchingRow(TraderAccount.class, eq("organizationId", org));
        db.create(TraderAccountTransaction::new, e -> {
          e.setOrganizationId(org);
          e.setLoanId(loanId);
          e.setType("BUY_IN_EXECUTION_FEE");
          e.setAmount(BigDecimal.ZERO);
          e.setExecutionFeeAmount(costs);
          e.setBalanceAfter(money(account, "available"));
          e.setReference(reference);
        });
      }
    }

    private void collateralApplied(long org, long id, Map<String, Object> d) {
      var p = db.owned(CollateralPosition.class, org, id);
      BigDecimal applied = decimal(d, "amount");
      require(
        "CASH".equals(p.get("asset_type")) &&
          Set.of("POSTED", "RELEASE_REQUESTED").contains(p.get("status")) &&
          applied.signum() > 0 &&
          applied.compareTo(money(p, "cash_amount")) <= 0,
        "Invalid buy-in collateral application"
      );
      db.edit(CollateralPosition.class, id, e -> {
        e.setCashAmount(e.getCashAmount().subtract(applied));
        if (e.getCashAmount().signum() == 0) {
          e.setStatus("APPLIED");
          e.setReleaseRequestStatus("COMPLETED");
          e.setRequestedReleaseCash(null);
        }
      });
      db.editMatching(
        TraderAccount.class,
        e -> {
          require(
            e.getPledged().compareTo(applied) >= 0,
            "Pledged cash is inconsistent"
          );
          e.setPledged(e.getPledged().subtract(applied));
        },
        eq("organizationId", org)
      );
    }

    private void buyinClosed(
      long org,
      long id,
      Map<String, Object> d,
      Instant at
    ) {
      var loan = db.owned(Loan.class, org, id);
      long quantity = number(d, "closedQuantity"),
        security = n(loan, "security_id");
      require(
        "CLOSED".equals(text(d, "status")) &&
          number(d, "outstandingQuantity") == 0 &&
          quantity == n(loan, "outstanding_quantity") &&
          quantity > 0,
        "Buy-in closeout quantity does not match the loan"
      );
      for (var r : db.rows(
        LoanReturn.class,
        eq("organizationId", org),
        eq("loanId", id),
        oneOf("status", "PENDING", "FAILED"),
        eq("assetsReleased", false)
      ))
        returned(org, n(r, "id"), false);
      for (var order : db.rows(
        TradeOrder.class,
        eq("organizationId", org),
        eq("securityId", security),
        eq("source", "BORROWED"),
        eq("status", "PENDING")
      ))
        trading.cancel(org, n(order, "id"));
      var h = db.find(Holding.class, new HoldingId(org, security));
      require(h != null, "Loan holding is missing");
      long converted = Math.min(
        quantity,
        h.getBorrowedQuantity() - h.getBlockedBorrowed()
      );
      require(converted >= 0, "Borrowed holding is inconsistent");
      BigDecimal price = decimal(d, "buyinPrice");
      long total = Math.addExact(h.getOwnedQuantity(), converted);
      if (converted > 0) {
        h.setAverageCost(
          h
            .getAverageCost()
            .multiply(BigDecimal.valueOf(h.getOwnedQuantity()))
            .add(price.multiply(BigDecimal.valueOf(converted)))
            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP)
        );
        h.setOwnedQuantity(total);
        h.setBorrowedQuantity(h.getBorrowedQuantity() - converted);
      }
      long shortQuantity = quantity - converted;
      BigDecimal pnl = BigDecimal.ZERO;
      for (var lot : db.rows(
        ShortLot.class,
        Sort.by("id"),
        Integer.MAX_VALUE,
        eq("organizationId", org),
        eq("securityId", security),
        greaterThan("openQuantity", 0L)
      )) {
        long covered = Math.min(shortQuantity, n(lot, "open_quantity"));
        pnl = pnl.add(
          money(lot, "price")
            .subtract(price)
            .multiply(BigDecimal.valueOf(covered))
        );
        db.edit(ShortLot.class, n(lot, "id"), e ->
          e.setOpenQuantity(e.getOpenQuantity() - covered)
        );
        shortQuantity -= covered;
        if (shortQuantity == 0) break;
      }
      h.setRealizedPnl(h.getRealizedPnl().add(amount(pnl)));
      db.edit(Loan.class, id, e -> {
        e.setStatus("CLOSED");
        e.setOutstandingQuantity(0L);
        e.setClosedAt(
          d.get("closedAt") == null ? at : Instant.parse(text(d, "closedAt"))
        );
      });
      for (var r : db.rows(
        Recall.class,
        eq("organizationId", org),
        eq("loanId", id),
        oneOf("status", "OPEN", "PARTIALLY_FULFILLED", "OVERDUE")
      ))
        db.edit(Recall.class, n(r, "id"), e -> e.setStatus("CANCELLED"));
      events.write(org, "LOAN_BUY_IN_CLOSED", id, d);
    }

    private void settled(long org, long id, Instant at) {
        var loan = db.owned(Loan.class, org, id);
        require("PENDING_SETTLEMENT".equals(loan.get("status")), "Loan is not awaiting settlement");
        db.holding(org, n(loan, "security_id"));
        db.editMatching(
                Holding.class,
                entity -> {
                    entity.setBorrowedQuantity(
                            entity.getBorrowedQuantity() + (n(loan, "quantity")));
                },
                eq("organizationId", org),
                eq("securityId", n(loan, "security_id")));
        db.edit(
                Loan.class,
                id,
                entity -> {
                    entity.setStatus("ACTIVE");
                    entity.setSettledAt(at);
                    entity.setSettledOn(at.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate());
                });
    }

    private void loanRejected(long org, long id) {
        var loan = db.owned(Loan.class, org, id);
        require(
                "PENDING_SETTLEMENT".equals(loan.get("status")),
                "Only unsettled loans can be rejected");
        for (var p :
                db.rows(CollateralPosition.class, eq("loanId", id), eq("status", "PLEDGE_PENDING")))
            collateral.applyReleaseConfirmation(org, n(p, "id"), true);
        // Remote reservation restoration is a separate authoritative event, not a local
        // expiry/unlock decision.
        db.edit(
                Loan.class,
                id,
                entity -> {
                    entity.setStatus("REJECTED");
                    entity.setOutstandingQuantity(0L);
                });
    }

    private void returned(long org, long id, boolean settled) {
        var r = db.owned(LoanReturn.class, org, id);
        var returned = db.find(LoanReturn.class, id);
        if (returned.isAssetsReleased() && !settled) return;
        require(("PENDING".equals(r.get("status")) || "FAILED".equals(r.get("status")))
                && !returned.isAssetsReleased(), "Return is not awaiting settlement or cancellation");
        var loan = db.owned(Loan.class, org, n(r, "loan_id"));
        if (settled) {
            long remaining = n(loan, "outstanding_quantity") - n(r, "quantity");
            require(remaining >= 0, "Return exceeds outstanding quantity");
            db.edit(
                    Loan.class,
                    n(loan, "id"),
                    entity -> {
                        entity.setOutstandingQuantity(remaining);
                    });
            db.edit(
                    LoanReturn.class,
                    id,
                    entity -> {
                        entity.setStatus("SETTLED");
                        entity.setSettledAt(Instant.now());
                    });
            if (remaining == 0) {
                db.edit(
                        Loan.class,
                        n(loan, "id"),
                        entity -> {
                            entity.setStatus("CLOSED");
                            entity.setClosedAt(Instant.now());
                        });
            }
            payments.submitBlockedReturnFees(org, id);
        } else {
            payments.releaseBlockedReturnFees(org, id);
            var h =
                    db.matchingRow(
                            Holding.class,
                            eq("organizationId", org),
                            eq("securityId", n(loan, "security_id")));
            long old = n(h, "owned_quantity"),
                    restore = n(r, "owned_quantity"),
                    total = old + restore;
            BigDecimal cost =
                    total == 0
                            ? BigDecimal.ZERO
                            : money(h, "average_cost")
                                    .multiply(BigDecimal.valueOf(old))
                                    .add(
                                            money(r, "owned_cost")
                                                    .multiply(BigDecimal.valueOf(restore)))
                                    .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
            db.editMatching(
                    Holding.class,
                    entity -> {
                        entity.setOwnedQuantity(entity.getOwnedQuantity() + (restore));
                        entity.setBorrowedQuantity(
                                entity.getBorrowedQuantity() + (n(r, "borrowed_quantity")));
                        entity.setAverageCost(cost);
                    },
                    eq("organizationId", org),
                    eq("securityId", n(loan, "security_id")));
            db.edit(
                    LoanReturn.class,
                    id,
                    entity -> {
                        entity.setStatus(entity.getFailureEventId() == null ? "REJECTED" : "FAILED");
                        entity.setAssetsReleased(true);
                    });
        }
        if (n(db.owned(Loan.class, org, n(loan, "id")), "outstanding_quantity") > 0
                && db.rows(LoanReturn.class, eq("loanId", n(loan, "id")), eq("status", "PENDING"))
                        .isEmpty()
                && db.rows(LoanReturn.class, eq("loanId", n(loan, "id")),
                        eq("status", "FAILED"), eq("assetsReleased", false)).isEmpty())
            db.editMatching(
                    Loan.class,
                    entity -> {
                        entity.setStatus("ACTIVE");
                    },
                    eq("id", n(loan, "id")),
                    eq("status", "RETURN_PENDING"));
    }

    private void fee(long org, long id, Map<String, Object> d) {
        db.owned(Loan.class, org, number(d, "loanId"));
        BigDecimal value = decimal(d, "amount");
        require(value.signum() > 0, "Fee bill must be positive");
        require(
                db.rows(LendingFee.class, eq("id", id)).isEmpty(),
                "Fee bill already exists; issue a new adjustment bill");
        db.persist(
                LendingFee::new,
                entity -> {
                    entity.setId(id);
                    entity.setOrganizationId(org);
                    entity.setLoanId(number(d, "loanId"));
                    entity.setPeriodStart(dateValue(d, "periodStart"));
                    entity.setPeriodEnd(dateValue(d, "periodEnd"));
                    entity.setFeeRate(decimal(d, "rate"));
                    entity.setFeeAmount(value);
                    entity.setDueDate(dateValue(d, "dueDate"));
                    entity.setStatus("DUE");
                });
        // The final owner-calculated bill can arrive after the return is closed. Reserve its cash
        // and send the receipt straight back to the lender instead of leaving it due forever.
        if ("CLOSED".equals(db.owned(Loan.class, org, number(d, "loanId")).get("status")))
            payments.autoSettleClosedLoanFee(org, id);
    }

    private void accrual(BorrowerEvent e) {
        var d = e.data();
        db.owned(Loan.class, e.organizationId(), number(d, "loanId"));
        String type = text(d, "type");
        require(Set.of("FEE", "REBATE").contains(type), "Invalid accrual type");
        db.persist(
                FeeAccrualView::new,
                entity -> {
                    entity.setId(e.referenceId());
                    entity.setOrganizationId(e.organizationId());
                    entity.setLoanId(number(d, "loanId"));
                    entity.setAccrualDate(dateValue(d, "date"));
                    entity.setType(type);
                    entity.setAmount(decimal(d, "amount"));
                    entity.setBaseValue(decimal(d, "baseValue"));
                    entity.setRate(decimal(d, "rate"));
                    entity.setSourceEventId(e.eventId());
                });
    }

    private void rebate(long org, long id, Map<String, Object> d) {
        db.owned(Loan.class, org, number(d, "loanId"));
        require(db.rows(Rebate.class, eq("id", id)).isEmpty(), "Rebate accrual already exists");
        db.persist(
                Rebate::new,
                entity -> {
                    entity.setId(id);
                    entity.setOrganizationId(org);
                    entity.setLoanId(number(d, "loanId"));
                    entity.setRebateRate(decimal(d, "rate"));
                    entity.setAmount(decimal(d, "amount"));
                    entity.setAccrualDate(dateValue(d, "date"));
                    entity.setNextPaymentDate(dateValue(d, "paymentDate"));
                    if (d.get("periodStart") != null) {
                        entity.setPeriodStart(dateValue(d, "periodStart"));
                        entity.setPeriodEnd(dateValue(d, "periodEnd"));
                        entity.setSettlementFrequency(text(d, "settlementFrequency"));
                    }
                    entity.setStatus("ACCRUED");
                });
    }

    private void rebatePaid(long org, long id, Map<String, Object> d, Instant paidAt) {
        var r = db.owned(Rebate.class, org, id);
        require("ACCRUED".equals(r.get("status")), "Rebate is not awaiting payment");
        BigDecimal value = decimal(d, "amount");
        require(
                value.compareTo(money(r, "amount")) == 0,
                "Rebate payment does not match reported accrual");
        db.create(
                RebatePayment::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setLoanId(n(r, "loan_id"));
                    entity.setRebateId(id);
                    entity.setAmount(value);
                    entity.setPaymentDate(paidAt);
                });
        accounts.changeCash(org, value, "REBATE_RECEIVED", String.valueOf(id));
        db.edit(
                Rebate.class,
                id,
                entity -> {
                    entity.setStatus("PAID");
                });
    }

    private void valuation(BorrowerEvent e) {
        var d = e.data();
        db.owned(Loan.class, e.organizationId(), number(d, "loanId"));
        db.create(
                LoanValuation::new,
                entity -> {
                    entity.setOrganizationId(e.organizationId());
                    entity.setLoanId(number(d, "loanId"));
                    entity.setValuedAt(e.occurredAt());
                    entity.setLoanMarketValue(decimal(d, "loanMarketValue"));
                    entity.setRequiredCollateral(decimal(d, "requiredCollateral"));
                    entity.setRecognizedCollateral(decimal(d, "recognizedCollateral"));
                    entity.setCoverageRatio(decimal(d, "coverageRatio"));
                    entity.setShortfall(decimal(d, "shortfall"));
                    entity.setSourceEventId(e.eventId());
                });
    }

    private void margin(long org, long id, Map<String, Object> d) {
        if (Boolean.TRUE.equals(d.get("cancellationRequested"))) {
            var snapshot = new LinkedHashMap<>(d);
            snapshot.remove("cancellationRequested");
            var existing = db.rows(MarginCall.class, eq("id", id));
            if (existing.isEmpty() || (d.get("issuedAt") != null
                && instantValue(d, "issuedAt").isAfter(instant(db.owned(MarginCall.class, org, id), "request_time")))) {
                margin(org, id, snapshot);
            }
            var call = db.owned(MarginCall.class, org, id);
            require(n(call, "loan_id") == number(d, "loanId"), "Margin cancellation loan mismatch");
            boolean submitted = db.rows(CollateralPosition.class, eq("organizationId", org), eq("marginCallId", id)).stream()
                .anyMatch(p -> !Set.of("FAILED", "REJECTED", "CANCELLED").contains(p.get("status"))
                    && db.rows(CollateralHistory.class, eq("positionId", n(p, "id"))).stream()
                        .noneMatch(h -> "PLEDGE_REQUESTED".equals(h.get("type"))
                            && instant(h, "created_at").isBefore(instant(call, "request_time"))));
            boolean accepted = money(call, "fulfilled_amount").signum() == 0 && !submitted
                && Set.of("OPEN", "OVERDUE", "CANCELLED").contains(call.get("status"));
            if (accepted) db.edit(MarginCall.class, id, entity -> entity.setStatus("CANCELLED"));
            events.write(org, "MARGIN_CALL_CANCELLATION_RESPONDED", number(d, "loanId"), Map.of(
                "marginCallId", id, "cancellationReference", text(d, "cancellationReference"), "accepted", accepted,
                "reason", accepted ? "Cancelled before collateral submission" : "Collateral has already been submitted or the call is resolved"));
            return;
        }
        long loan = number(d, "loanId");
        db.owned(Loan.class, org, loan);
        String reportedStatus = obligationStatus(d);
        String status = "PENDING".equals(reportedStatus) ? "OPEN" : reportedStatus;
        if (db.rows(MarginCall.class, eq("id", id)).isEmpty())
            db.persist(
                    MarginCall::new,
                    entity -> {
                        entity.setId(id);
                        entity.setOrganizationId(org);
                        entity.setLoanId(loan);
                        entity.setExternalId(text(d, "externalId"));
                        entity.setAmount(decimal(d, "amount"));
                        entity.setFulfilledAmount(decimal(d, "fulfilledAmount"));
                        entity.setReason(text(d, "reason"));
                        entity.setStatus(status);
                        entity.setDueAt(instantValue(d, "dueAt"));
                        if (d.get("issuedAt") != null) entity.setRequestTime(instantValue(d, "issuedAt"));
                    });
        else {
            db.owned(MarginCall.class, org, id);
            db.edit(
                    MarginCall.class,
                    id,
                    entity -> {
                        entity.setAmount(decimal(d, "amount"));
                        entity.setFulfilledAmount(decimal(d, "fulfilledAmount"));
                        entity.setStatus(status);
                        entity.setDueAt(instantValue(d, "dueAt"));
                        entity.setExternalId(text(d, "externalId"));
                        entity.setReason(text(d, "reason"));
                        if (d.get("issuedAt") != null) entity.setRequestTime(instantValue(d, "issuedAt"));
                    });
        }
    }

    private void recall(long org, long id, Map<String, Object> d) {
        long loan = number(d, "loanId");
        db.owned(Loan.class, org, loan);
        String status = obligationStatus(d);
        if (db.rows(Recall.class, eq("id", id)).isEmpty())
            db.persist(
                    Recall::new,
                    entity -> {
                        entity.setId(id);
                        entity.setOrganizationId(org);
                        entity.setLoanId(loan);
                        entity.setExternalId(text(d, "externalId"));
                        entity.setQuantity(number(d, "quantity"));
                        entity.setFulfilledQuantity(number(d, "fulfilledQuantity"));
                        entity.setReason(text(d, "reason"));
                        entity.setStatus(status);
                        entity.setDueAt(instantValue(d, "dueAt"));
                    });
        else {
            db.owned(Recall.class, org, id);
            db.edit(
                    Recall.class,
                    id,
                    entity -> {
                        entity.setQuantity(number(d, "quantity"));
                        entity.setFulfilledQuantity(number(d, "fulfilledQuantity"));
                        entity.setStatus(status);
                        entity.setDueAt(instantValue(d, "dueAt"));
                    });
        }
    }

    private void defaultView(long org, long id, Map<String, Object> d) {
        long loan = number(d, "loanId");
        db.owned(Loan.class, org, loan);
        if (db.rows(LoanDefault.class, eq("id", id)).isEmpty())
            db.persist(
                    LoanDefault::new,
                    entity -> {
                        entity.setId(id);
                        entity.setOrganizationId(org);
                        entity.setLoanId(loan);
                        entity.setObligationId(number(d, "obligationId"));
                        entity.setObligationType(text(d, "obligationType"));
                        entity.setReason(text(d, "reason"));
                        entity.setStatus(text(d, "status"));
                    });
        else {
            db.owned(LoanDefault.class, org, id);
            db.edit(
                    LoanDefault.class,
                    id,
                    entity -> {
                        entity.setStatus(text(d, "status"));
                    });
        }
    }

    private void buyInView(long org, long id, Map<String, Object> d) {
        db.owned(LoanDefault.class, org, number(d, "defaultId"));
        require(db.rows(BuyIn.class, eq("id", id)).isEmpty(), "Buy-in projection already exists");
        db.persist(
                BuyIn::new,
                entity -> {
                    entity.setId(id);
                    entity.setOrganizationId(org);
                    entity.setDefaultId(number(d, "defaultId"));
                    entity.setQuantity(number(d, "quantity"));
                    entity.setPrice(decimal(d, "price"));
                    entity.setCosts(decimal(d, "costs"));
                    entity.setCollateralApplied(decimal(d, "collateralApplied"));
                    entity.setResidualBalance(decimal(d, "residualBalance"));
                    entity.setStatus(text(d, "status"));
                });
    }

    private static String obligationStatus(Map<String, Object> d) {
        String s = text(d, "status");
        require(
                Set.of(
                                "PENDING",
                                "OPEN",
                                "PARTIALLY_FULFILLED",
                                "FULFILLED",
                                "OVERDUE",
                                "CANCELLED")
                        .contains(s),
                "Invalid obligation status");
        return s;
    }

    private static String text(Map<String, Object> d, String key) {
        Object v = d.get(key);
        require(v != null && !v.toString().isBlank(), "Missing event field " + key);
        return v.toString();
    }

    private static long number(Map<String, Object> d, String key) {
        return new BigDecimal(text(d, key)).longValueExact();
    }

    private static BigDecimal decimal(Map<String, Object> d, String key) {
        BigDecimal value = new BigDecimal(text(d, key));
        require(value.signum() >= 0, "Negative event amount " + key);
        return value;
    }

    private static LocalDate dateValue(Map<String, Object> d, String key) {
        return LocalDate.parse(text(d, key));
    }

    private static Instant instantValue(Map<String, Object> d, String key) {
        return Instant.parse(text(d, key));
    }
}
