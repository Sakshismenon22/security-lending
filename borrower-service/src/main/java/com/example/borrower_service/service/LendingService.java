package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.dto.LocateRequest;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.*;
import java.util.*;

@Service
@Transactional
public class LendingService {
    private final BorrowerRepository db;
    private final EventWriter events;
    private final MarketService market;
    private final CollateralService collateral;
    private final Clock clock;

    public LendingService(
            BorrowerRepository db,
            EventWriter events,
            MarketService market,
            CollateralService collateral,
            Clock clock) {
        this.db = db;
        this.events = events;
        this.market = market;
        this.collateral = collateral;
        this.clock = clock;
    }

    public Map<String, Object> propose(long org, AgreementRequest r) {
        db.lock(org);
        db.row(LenderReference.class, r.lenderId());
        require(
                !r.expiryDate().isBefore(r.effectiveDate()),
                "Agreement expires before its effective date");
        require(r.dayBasis() == 360 || r.dayBasis() == 365, "Day basis must be 360 or 365");
        var seen = new HashSet<String>();
        for (var rule : r.collateralRules()) {
            boolean cash = "CASH".equals(rule.assetType());
            require(
                    cash
                            ? (rule.securityId() == null && rule.haircut().signum() == 0)
                            : rule.securityId() != null,
                    "Cash has no security or haircut; security collateral requires securityId");
            if (!cash) db.row(SecurityMaster.class, rule.securityId());
            require(
                    seen.add(rule.assetType() + ":" + rule.securityId()),
                    "Duplicate collateral eligibility rule");
        }
        long id =
                db.create(
                        Msla::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setLenderId(r.lenderId());
                            entity.setNumber(r.number());
                            entity.setStatus("PROPOSED");
                            entity.setEffectiveDate(r.effectiveDate());
                            entity.setExpiryDate(r.expiryDate());
                            entity.setCreditLimit(r.creditLimit());
                            entity.setFeeRate(r.feeRate());
                            entity.setRebateRate(r.rebateRate());
                            entity.setCoverageRatio(r.coverageRatio());
                            entity.setBillingFrequency(r.billingFrequency());
                            entity.setRebateSettlementFrequency(r.rebateSettlementFrequency());
                            entity.setDayBasis(r.dayBasis());
                            entity.setRecallAllowed(r.recallAllowed());
                            entity.setMarginHours(r.marginHours());
                            entity.setRecallHours(r.recallHours());
                        });
        for (var rule : r.collateralRules()) {
            long asset = n(db.matchingRow(Asset.class, eq("assetName", rule.assetType())), "id");
            db.create(
                    CollateralHaircut::new,
                    entity -> {
                        entity.setMslaId(id);
                        entity.setAssetType(rule.assetType());
                        entity.setSecurityId(rule.securityId());
                        entity.setHaircut(rule.haircut());
                        entity.setAssetId(asset);
                    });
        }
        events.write(org, "AGREEMENT_PROPOSED", id, r);
        return db.owned(Msla.class, org, id);
    }

    private Map<String, Object> active(long org, long id) {
        var a = db.owned(Msla.class, org, id);
        var today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata")));
        require("ACTIVE".equals(a.get("status")), "MSLA is not active");
        require(
                !today.isBefore(date(a, "effective_date"))
                        && !today.isAfter(date(a, "expiry_date")),
                "MSLA is outside its effective dates");
        return a;
    }

    public Map<String, Object> locate(long org, LocateRequest r) {
        db.lock(org);
        active(org, r.agreementId());
        db.row(SecurityMaster.class, r.securityId());
        require(r.expiresAt().isAfter(Instant.now()), "Locate expiry must be in the future");
        long id =
                db.create(
                        com.example.borrower_service.model.LocateRequest::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setMslaId(r.agreementId());
                            entity.setSecurityId(r.securityId());
                            entity.setQuantity(r.quantity());
                            entity.setStatus("PENDING");
                            entity.setExpiresAt(r.expiresAt());
                        });
        events.write(org, "LOCATE_REQUESTED", id, r);
        return db.owned(com.example.borrower_service.model.LocateRequest.class, org, id);
    }

    public Map<String, Object> releaseReservation(long org, long id) {
        db.lock(org);
        var r = db.owned(Reservation.class, org, id);
        require("ACTIVE".equals(r.get("status")), "Reservation is not active");
        db.edit(
                Reservation.class,
                id,
                entity -> {
                    entity.setStatus("RELEASE_REQUESTED");
                });
        events.write(
                org,
                "RESERVATION_RELEASE_REQUESTED",
                id,
                Map.of("quantity", n(r, "remaining_quantity")));
        return db.owned(Reservation.class, org, id);
    }

    public Map<String, Object> reserve(long org, long locateId, ReservationRequest request) {
        db.lock(org);
        var locate =
                db.owned(com.example.borrower_service.model.LocateRequest.class, org, locateId);
        require(
                Set.of("APPROVED", "PARTIALLY_APPROVED").contains(locate.get("status")),
                "Locate must be approved before reservation");
        require(
                instant(locate, "expires_at").isAfter(Instant.now()),
                "Locate approval has expired");
        require(
                request.quantity() <= n(locate, "approved_quantity"),
                "Reservation exceeds approved locate quantity");
        require(
                db.rows(Reservation.class, eq("locateId", locateId)).isEmpty(),
                "This locate already has a reservation request");
        long id =
                db.create(
                        Reservation::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setLocateId(locateId);
                            entity.setMslaId(n(locate, "msla_id"));
                            entity.setSecurityId(n(locate, "security_id"));
                            entity.setQuantity(request.quantity());
                            entity.setRemainingQuantity(0L);
                            entity.setStatus("REQUESTED");
                            entity.setExpiresAt(instant(locate, "expires_at"));
                        });
        events.write(
                org,
                "RESERVATION_REQUESTED",
                id,
                Map.of("locateId", locateId, "quantity", request.quantity()));
        return db.owned(Reservation.class, org, id);
    }

    public BigDecimal exposure(long org, long agreement) {
        BigDecimal result = BigDecimal.ZERO;
        for (var l :
                db.rows(
                        Loan.class,
                        eq("organizationId", org),
                        eq("mslaId", agreement),
                        oneOf(
                                "status",
                                "PENDING_SETTLEMENT",
                                "ACTIVE",
                                "RETURN_PENDING",
                                "DEFAULTED")))
            result =
                    result.add(
                            market.price(n(l, "security_id"))
                                    .value()
                                    .multiply(BigDecimal.valueOf(n(l, "outstanding_quantity"))));
        return amount(result);
    }

    public Map<String, Object> createLoan(long org, LoanRequest r) {
        db.lock(org);
        var reservation = db.owned(Reservation.class, org, r.reservationId());
        require(
                "ACTIVE".equals(reservation.get("status"))
                        && instant(reservation, "expires_at").isAfter(Instant.now()),
                "Reservation is not active or has expired");
        require(
                n(reservation, "remaining_quantity") >= r.quantity(),
                "Loan quantity exceeds remaining reservation");
        var a = active(org, n(reservation, "msla_id"));
        BigDecimal before = exposure(org, n(a, "id")),
                value =
                        market.price(n(reservation, "security_id"))
                                .value()
                                .multiply(BigDecimal.valueOf(r.quantity()));
        require(
                before.add(value).compareTo(money(a, "credit_limit")) <= 0,
                "MSLA credit limit exceeded");
        long id =
                db.create(
                        Loan::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setMslaId(n(a, "id"));
                            entity.setReservationId(r.reservationId());
                            entity.setSecurityId(n(reservation, "security_id"));
                            entity.setQuantity(r.quantity());
                            entity.setOutstandingQuantity(r.quantity());
                            entity.setStatus("PENDING_SETTLEMENT");
                            entity.setFeeRate(money(a, "fee_rate"));
                            entity.setRebateRate(money(a, "rebate_rate"));
                            entity.setCoverageRatio(money(a, "coverage_ratio"));
                            entity.setDayBasis(Math.toIntExact(n(a, "day_basis")));
                            entity.setBillingFrequency((String) a.get("billing_frequency"));
                        });
        db.edit(
                Reservation.class,
                r.reservationId(),
                entity -> {
                    entity.setRemainingQuantity(entity.getRemainingQuantity() - (r.quantity()));
                });
        if (n(reservation, "remaining_quantity") == r.quantity())
            db.edit(
                    Reservation.class,
                    r.reservationId(),
                    entity -> {
                        entity.setStatus("CONSUMED");
                    });
        for (var allocation : r.collateral()) collateral.post(org, id, allocation);
        collateral.validateInitialAllocation(org, id);
        credit(org, a, "LOAN_REQUESTED", before, before.add(value));
        events.write(org, "LOAN_REQUESTED", id, r);
        return db.owned(Loan.class, org, id);
    }

    public Map<String, Object> requestReturn(long org, long loanId, ReturnRequest r) {
        db.lock(org);
        var loan = db.owned(Loan.class, org, loanId);
        require(
                Set.of("ACTIVE", "RETURN_PENDING").contains(loan.get("status")),
                "Loan is not active");
        if (r.recallId() != null) {
            var recall = db.owned(Recall.class, org, r.recallId());
            require(
                    n(recall, "loan_id") == loanId
                            && Set.of("OPEN", "PARTIALLY_FULFILLED", "OVERDUE")
                                    .contains(recall.get("status")),
                    "Recall does not belong to this loan or is already resolved");
            long pendingForRecall =
                    db.sum(
                            LoanReturn.class,
                            "quantity",
                            Long.class,
                            eq("recallId", r.recallId()),
                            eq("status", "PENDING"));
            require(
                    r.quantity()
                            <= n(recall, "quantity")
                                    - n(recall, "fulfilled_quantity")
                                    - pendingForRecall,
                    "Return exceeds the uncommitted recall quantity");
        }
        long pending =
                db.sum(
                        LoanReturn.class,
                        "quantity",
                        Long.class,
                        eq("loanId", loanId),
                        eq("status", "PENDING"));
        require(
                r.quantity() <= n(loan, "outstanding_quantity") - pending,
                "Return exceeds uncommitted outstanding loan quantity");
        long security = n(loan, "security_id");
        db.holding(org, security);
        var h =
                db.matchingRow(
                        Holding.class, eq("organizationId", org), eq("securityId", security));
        long borrowed =
                Math.min(r.quantity(), n(h, "borrowed_quantity") - n(h, "blocked_borrowed"));
        long owned = r.quantity() - borrowed;
        require(
                n(h, "owned_quantity") - n(h, "pledged_quantity") - n(h, "blocked_owned") >= owned,
                "Buy back sufficient unpledged securities before returning the loan");
        db.editMatching(
                Holding.class,
                entity -> {
                    entity.setOwnedQuantity(entity.getOwnedQuantity() - (owned));
                    entity.setBorrowedQuantity(entity.getBorrowedQuantity() - (borrowed));
                },
                eq("organizationId", org),
                eq("securityId", security));
        long id =
                db.create(
                        LoanReturn::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setLoanId(loanId);
                            entity.setQuantity(r.quantity());
                            entity.setOwnedQuantity(owned);
                            entity.setBorrowedQuantity(borrowed);
                            entity.setStatus("PENDING");
                            entity.setOwnedCost(money(h, "average_cost"));
                            entity.setRecallId(r.recallId());
                        });
        db.edit(
                Loan.class,
                loanId,
                entity -> {
                    entity.setStatus("RETURN_PENDING");
                });
        events.write(
                org,
                "RETURN_REQUESTED",
                id,
                Map.of(
                        "loanId",
                        loanId,
                        "quantity",
                        r.quantity(),
                        "recallId",
                        r.recallId() == null ? 0 : r.recallId()));
        return db.owned(LoanReturn.class, org, id);
    }

    public Map<String, Object> respondToRecall(long org, long recallId, ReturnRequest r) {
        db.lock(org);
        var recall = db.owned(Recall.class, org, recallId);
        require(
                r.recallId() == null || r.recallId() == recallId,
                "Recall reference does not match the request path");
        return requestReturn(org, n(recall, "loan_id"), new ReturnRequest(r.quantity(), recallId));
    }

    private void credit(
            long org, Map<String, Object> a, String type, BigDecimal before, BigDecimal after) {
        BigDecimal limit = money(a, "credit_limit");
        db.create(
                CreditHistory::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setMslaId(n(a, "id"));
                    entity.setTransactionType(type);
                    entity.setOldLimit(limit);
                    entity.setNewLimit(limit);
                    entity.setCreditChange(after.subtract(before));
                    entity.setUtilizedBefore(before);
                    entity.setUtilizedAfter(after);
                    entity.setAvailableBefore(limit.subtract(before));
                    entity.setAvailableAfter(limit.subtract(after));
                });
    }
}
