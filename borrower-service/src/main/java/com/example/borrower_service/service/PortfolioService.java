package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class PortfolioService {
    private final BorrowerRepository db;
    private final CollateralService collateral;
    private final LendingService lending;

    public PortfolioService(
            BorrowerRepository db, CollateralService collateral, LendingService lending) {
        this.db = db;
        this.collateral = collateral;
        this.lending = lending;
    }

    public Object account(long org) {
        return db.matchingRow(TraderAccount.class, eq("organizationId", org));
    }

    public Object lenders(long org) {
        db.row(Organization.class, org);
        return db.rows(LenderReference.class, Sort.by(Sort.Order.asc("name")), Integer.MAX_VALUE);
    }

    public Object holdings(long org) {
        db.find(Organization.class, org);
        var holdings =
                db.entities(
                        Holding.class,
                        Sort.unsorted(),
                        Integer.MAX_VALUE,
                        eq("organizationId", org));
        var securities = securities(holdings.stream().map(Holding::getSecurityId).toList());
        return holdings.stream()
                .sorted(Comparator.comparing(h -> securities.get(h.getSecurityId()).getSymbol()))
                .map(
                        holding -> {
                            var security = securities.get(holding.getSecurityId());
                            var result = columns(holding);
                            result.putAll(marketFields(security));
                            result.put("name", security.getName());
                            result.put(
                                    "available_owned_quantity",
                                    holding.getOwnedQuantity()
                                            - holding.getPledgedQuantity()
                                            - holding.getBlockedOwned());
                            result.put(
                                    "available_borrowed_quantity",
                                    holding.getBorrowedQuantity() - holding.getBlockedBorrowed());
                            result.put(
                                    "unrealized_pnl",
                                    security.getPrice() == null
                                            ? null
                                            : security.getPrice()
                                                    .subtract(holding.getAverageCost())
                                                    .multiply(
                                                            BigDecimal.valueOf(
                                                                    holding.getOwnedQuantity())));
                            return result;
                        })
                .toList();
    }

    public Object positions(long org) {
        db.find(Organization.class, org);
        var holdings = new HashMap<Long, Holding>();
        for (var holding :
                db.entities(
                        Holding.class,
                        Sort.unsorted(),
                        Integer.MAX_VALUE,
                        eq("organizationId", org))) {
            holdings.put(holding.getSecurityId(), holding);
        }
        var lotsBySecurity = new HashMap<Long, List<ShortLot>>();
        for (var lot :
                db.entities(
                        ShortLot.class,
                        Sort.unsorted(),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        greaterThan("openQuantity", 0))) {
            lotsBySecurity
                    .computeIfAbsent(lot.getSecurityId(), ignored -> new ArrayList<>())
                    .add(lot);
        }
        var ids = new HashSet<>(lotsBySecurity.keySet());
        holdings.values().stream()
                .filter(h -> h.getOwnedQuantity() > 0)
                .map(Holding::getSecurityId)
                .forEach(ids::add);
        return securities(ids).values().stream()
                .sorted(Comparator.comparing(SecurityMaster::getSymbol))
                .map(
                        security -> {
                            var holding = holdings.get(security.getId());
                            var lots = lotsBySecurity.getOrDefault(security.getId(), List.of());
                            long quantity =
                                    lots.stream().mapToLong(ShortLot::getOpenQuantity).sum();
                            BigDecimal cost =
                                    lots.stream()
                                            .map(
                                                    lot ->
                                                            lot.getPrice()
                                                                    .multiply(
                                                                            BigDecimal.valueOf(
                                                                                    lot
                                                                                            .getOpenQuantity())))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                            BigDecimal average =
                                    quantity == 0
                                            ? null
                                            : cost.divide(
                                                    BigDecimal.valueOf(quantity),
                                                    MathContext.DECIMAL128);
                            var result = marketFields(security);
                            result.put("security_id", security.getId());
                            result.put(
                                    "long_quantity",
                                    holding == null ? 0L : holding.getOwnedQuantity());
                            result.put(
                                    "realized_pnl",
                                    holding == null ? BigDecimal.ZERO : holding.getRealizedPnl());
                            result.put("short_quantity", quantity);
                            result.put("average_entry", average);
                            result.put(
                                    "short_unrealized_pnl",
                                    average == null || security.getPrice() == null
                                            ? null
                                            : average.subtract(security.getPrice())
                                                    .multiply(BigDecimal.valueOf(quantity)));
                            return result;
                        })
                .toList();
    }

    private Map<Long, SecurityMaster> securities(Collection<Long> ids) {
        var result = new HashMap<Long, SecurityMaster>();
        if (!ids.isEmpty()) {
            for (var security :
                    db.entities(
                            SecurityMaster.class,
                            Sort.unsorted(),
                            Integer.MAX_VALUE,
                            oneOf("id", ids.toArray()))) {
                result.put(security.getId(), security);
            }
        }
        return result;
    }

    private Map<String, Object> marketFields(SecurityMaster security) {
        var result = new LinkedHashMap<String, Object>();
        result.put("symbol", security.getSymbol());
        result.put("exchange", security.getExchange());
        result.put("price", security.getPrice());
        result.put("price_at", security.getPriceAt());
        return result;
    }

    public Object dashboard(long org) {
        var out = new LinkedHashMap<String, Object>();
        out.put("account", account(org));
        var loans =
                db.entities(
                        Loan.class,
                        Sort.unsorted(),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        oneOf("status", "ACTIVE", "RETURN_PENDING", "DEFAULTED"));
        var securities = securities(loans.stream().map(Loan::getSecurityId).toList());
        BigDecimal borrowed = BigDecimal.ZERO;
        for (var loan : loans) {
            BigDecimal price = securities.get(loan.getSecurityId()).getPrice();
            if (price != null)
                borrowed =
                        borrowed.add(
                                price.multiply(BigDecimal.valueOf(loan.getOutstandingQuantity())));
        }
        out.put(
                "loans",
                Map.of("active_loans", (long) loans.size(), "borrowed_market_value", borrowed));
        out.put(
                "fees",
                Map.of(
                        "unpaid_fees",
                        db.sum(
                                LendingFee.class,
                                "feeAmount",
                                BigDecimal.class,
                                eq("organizationId", org),
                                eq("status", "DUE"))));
        out.put(
                "rebates",
                Map.of(
                        "rebates_receivable",
                        db.sum(
                                Rebate.class,
                                "amount",
                                BigDecimal.class,
                                eq("organizationId", org),
                                eq("status", "ACCRUED"))));
        out.put(
                "marginCalls",
                db.rows(
                        MarginCall.class,
                        Sort.by(Sort.Order.asc("dueAt")),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        oneOf("status", "OPEN", "PARTIALLY_FULFILLED", "OVERDUE")));
        out.put(
                "recalls",
                db.rows(
                        Recall.class,
                        Sort.by(Sort.Order.asc("dueAt")),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        oneOf("status", "OPEN", "PARTIALLY_FULFILLED", "OVERDUE")));
        out.put(
                "reservations",
                db.rows(
                        Reservation.class,
                        Sort.by(Sort.Order.asc("expiresAt")),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        eq("status", "ACTIVE")));
        out.put(
                "recentActivity",
                db.rows(
                        AuditEvent.class,
                        Sort.by(Sort.Order.desc("id")),
                        20,
                        eq("organizationId", org)));
        out.put(
                "pricePolicy",
                "Delayed completed-minute prices; inspect priceAt before relying on valuations");
        out.put("simulatedTrading", true);
        return out;
    }

    public Object agreement(long org, long id) {
        var a = new LinkedHashMap<>(db.owned(Msla.class, org, id));
        a.put("collateralRules", db.rows(CollateralHaircut.class, eq("mslaId", id)));
        try {
            var used = lending.exposure(org, id);
            a.put("utilizedCredit", used);
            a.put("availableCredit", money(a, "credit_limit").subtract(used));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            a.put("valuationError", e.getReason());
        }
        return a;
    }

    public Object loan(long org, long id) {
        var loan = new LinkedHashMap<>(db.owned(Loan.class, org, id));
        loan.put("collateral_position", loanRows(CollateralPosition.class, org, id));
        loan.put("loan_return", loanRows(LoanReturn.class, org, id));
        loan.put("lending_fee", loanRows(LendingFee.class, org, id));
        loan.put("rebate", loanRows(Rebate.class, org, id));
        loan.put("margin_recall", loanRows(MarginCall.class, org, id));
        loan.put("recall", loanRows(Recall.class, org, id));
        loan.put("valuation", authoritativeValuation(org, id));
        try {
            loan.put("allocationPreview", collateral.preview(org, id));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            loan.put("valuationError", e.getReason());
        }
        return loan;
    }

    private List<Map<String, Object>> loanRows(Class<?> type, long org, long id) {
        return db.rows(
                type,
                Sort.by("id"),
                Integer.MAX_VALUE,
                eq("organizationId", org),
                eq("loanId", id));
    }

    public Object authoritativeValuation(long org, long id) {
        db.owned(Loan.class, org, id);
        return db.rows(
                LoanValuation.class,
                Sort.by(Sort.Order.desc("valuedAt"), Sort.Order.desc("id")),
                1,
                eq("organizationId", org),
                eq("loanId", id));
    }

    @Transactional
    public Object watch(long org, long security, boolean add) {
        db.lock(org);
        db.row(SecurityMaster.class, security);
        if (add
                && db.rows(
                                WatchlistEntry.class,
                                eq("organizationId", org),
                                eq("securityId", security))
                        .isEmpty())
            db.create(
                    WatchlistEntry::new,
                    entity -> {
                        entity.setOrganizationId(org);
                        entity.setSecurityId(security);
                    });
        if (!add)
            db.delete(WatchlistEntry.class, eq("organizationId", org), eq("securityId", security));
        return watchlist(org);
    }

    public Object watchlist(long org) {
        db.find(Organization.class, org);
        var entries =
                db.entities(
                        WatchlistEntry.class,
                        Sort.unsorted(),
                        Integer.MAX_VALUE,
                        eq("organizationId", org));
        return securities(entries.stream().map(WatchlistEntry::getSecurityId).toList())
                .values()
                .stream()
                .sorted(Comparator.comparing(SecurityMaster::getSymbol))
                .map(BorrowerRepository::columns)
                .toList();
    }

    @Transactional
    public void markRead(long org, long id) {
        db.lock(org);
        db.owned(Notification.class, org, id);
        db.edit(
                Notification.class,
                id,
                entity -> {
                    entity.setIsRead(true);
                });
    }

    public String statement(long org) {
        db.row(Organization.class, org);
        var out = new StringBuilder("id,type,amount,balance_after,reference,created_at\r\n");
        for (var row :
                db.rows(
                        TraderAccountTransaction.class,
                        Sort.by(Sort.Order.asc("id")),
                        Integer.MAX_VALUE,
                        eq("organizationId", org))) {
            out.append(n(row, "id"));
            for (String key :
                    List.of("type", "amount", "balance_after", "reference", "created_at")) {
                String value = String.valueOf(row.get(key));
                if (value.matches("^[=+@].*")
                        || (value.startsWith("-") && !value.matches("-?[0-9]+(\\.[0-9]+)?")))
                    value = "'" + value;
                out.append(',').append('"').append(value.replace("\"", "\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString();
    }
}
