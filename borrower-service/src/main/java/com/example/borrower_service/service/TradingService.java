package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.time.Instant;
import java.util.*;

/** Paper execution against the delayed completed-minute close; no exchange execution claims. */
@Service
@Transactional
public class TradingService {
    private final BorrowerRepository db;
    private final MarketService market;
    private final DirectoryService accounts;
    private final EventWriter events;

    public TradingService(
            BorrowerRepository db,
            MarketService market,
            DirectoryService accounts,
            EventWriter events) {
        this.db = db;
        this.market = market;
        this.accounts = accounts;
        this.events = events;
    }

    public Map<String, Object> order(long org, OrderRequest r) {
        db.lock(org);
        var quote = market.price(r.securityId());
        require(!"LIMIT".equals(r.type()) || r.limitPrice() != null, "Limit price is required");
        require(
                !"MARKET".equals(r.type()) || r.limitPrice() == null,
                "Market orders must omit limitPrice");
        db.holding(org, r.securityId());
        var h =
                db.matchingRow(
                        Holding.class, eq("organizationId", org), eq("securityId", r.securityId()));
        BigDecimal reserve;
        if ("BUY".equals(r.side())) {
            if ("BORROWED".equals(r.source())) {
                long shorts = shortQuantity(org, r.securityId());
                long pending =
                        db.sum(
                                TradeOrder.class,
                                "quantity",
                                Long.class,
                                eq("organizationId", org),
                                eq("securityId", r.securityId()),
                                eq("side", "BUY"),
                                eq("source", "BORROWED"),
                                eq("status", "PENDING"));
                require(
                        r.quantity() <= shorts - pending,
                        "Buy-to-cover quantity exceeds the uncovered short position");
            }
            reserve =
                    amount(
                            ("LIMIT".equals(r.type()) ? r.limitPrice() : quote.value())
                                    .multiply(BigDecimal.valueOf(r.quantity())));
            accounts.changeCash(org, reserve.negate(), "ORDER_CASH_BLOCK", "new-order");
            var account = db.find(TraderAccount.class, org);
            account.setBlocked(account.getBlocked().add(reserve));
        } else if ("OWNED".equals(r.source())) {
            reserve = BigDecimal.ZERO;
            require(
                    n(h, "owned_quantity") - n(h, "pledged_quantity") - n(h, "blocked_owned")
                            >= r.quantity(),
                    "Insufficient unpledged owned securities");
            db.editMatching(
                    Holding.class,
                    entity -> {
                        entity.setBlockedOwned(entity.getBlockedOwned() + (r.quantity()));
                    },
                    eq("organizationId", org),
                    eq("securityId", r.securityId()));
        } else {
            reserve = BigDecimal.ZERO;
            require(
                    n(h, "borrowed_quantity") - n(h, "blocked_borrowed") >= r.quantity(),
                    "Insufficient settled borrowed securities; locate approval is not sellable"
                        + " inventory");
            db.editMatching(
                    Holding.class,
                    entity -> {
                        entity.setBlockedBorrowed(entity.getBlockedBorrowed() + (r.quantity()));
                    },
                    eq("organizationId", org),
                    eq("securityId", r.securityId()));
        }
        long id =
                db.create(
                        TradeOrder::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setSecurityId(r.securityId());
                            entity.setSide(r.side());
                            entity.setType(r.type());
                            entity.setSource(r.source());
                            entity.setQuantity(r.quantity());
                            entity.setLimitPrice(r.limitPrice());
                            entity.setReservedCash(reserve);
                            entity.setStatus("PENDING");
                        });
        events.write(org, "ORDER_SUBMITTED", id, r);
        match(org, id);
        return db.owned(TradeOrder.class, org, id);
    }

    public Map<String, Object> cancel(long org, long id) {
        db.lock(org);
        var order = db.owned(TradeOrder.class, org, id);
        require("PENDING".equals(order.get("status")), "Only pending orders can be cancelled");
        release(order);
        db.edit(
                TradeOrder.class,
                id,
                entity -> {
                    entity.setStatus("CANCELLED");
                });
        events.write(org, "ORDER_CANCELLED", id, Map.of());
        return db.owned(TradeOrder.class, org, id);
    }

    private void release(Map<String, Object> order) {
        long org = n(order, "organization_id"),
                s = n(order, "security_id"),
                q = n(order, "quantity");
        if ("BUY".equals(order.get("side"))) {
            BigDecimal reserved = money(order, "reserved_cash");
            db.editMatching(
                    TraderAccount.class,
                    entity -> {
                        entity.setBlocked(entity.getBlocked().subtract(reserved));
                    },
                    eq("organizationId", org));
            accounts.changeCash(
                    org, reserved, "ORDER_CASH_RELEASE", String.valueOf(order.get("id")));
        } else {
            var holding = db.find(Holding.class, new HoldingId(org, s));
            if ("OWNED".equals(order.get("source"))) {
                holding.setBlockedOwned(holding.getBlockedOwned() - q);
            } else {
                holding.setBlockedBorrowed(holding.getBlockedBorrowed() - q);
            }
        }
    }

    public void match(long org, long id) {
        db.lock(org);
        var order = db.owned(TradeOrder.class, org, id);
        if (!"PENDING".equals(order.get("status"))) return;
        long security = n(order, "security_id"), q = n(order, "quantity");
        var quote = market.price(security);
        boolean buy = "BUY".equals(order.get("side"));
        if ("LIMIT".equals(order.get("type"))) {
            int cmp = quote.value().compareTo(money(order, "limit_price"));
            if (buy ? cmp > 0 : cmp < 0) return;
        }
        var h =
                db.matchingRow(
                        Holding.class, eq("organizationId", org), eq("securityId", security));
        BigDecimal value = amount(quote.value().multiply(BigDecimal.valueOf(q)));
        release(order);
        if (buy) {
            accounts.changeCash(org, value.negate(), "TRADE_BUY", String.valueOf(id));
            long owned = n(h, "owned_quantity"), total = Math.addExact(owned, q);
            BigDecimal average =
                    money(h, "average_cost")
                            .multiply(BigDecimal.valueOf(owned))
                            .add(value)
                            .divide(BigDecimal.valueOf(total), 6, RoundingMode.HALF_UP);
            db.editMatching(
                    Holding.class,
                    entity -> {
                        entity.setOwnedQuantity(total);
                        entity.setAverageCost(average);
                    },
                    eq("organizationId", org),
                    eq("securityId", security));
            if ("BORROWED".equals(order.get("source"))) cover(org, security, q, quote.value());
        } else {
            accounts.changeCash(org, value, "TRADE_SELL", String.valueOf(id));
            if ("OWNED".equals(order.get("source"))) {
                BigDecimal pnl =
                        amount(
                                quote.value()
                                        .subtract(money(h, "average_cost"))
                                        .multiply(BigDecimal.valueOf(q)));
                db.editMatching(
                        Holding.class,
                        entity -> {
                            entity.setOwnedQuantity(entity.getOwnedQuantity() - (q));
                            entity.setRealizedPnl(entity.getRealizedPnl().add(pnl));
                        },
                        eq("organizationId", org),
                        eq("securityId", security));
            } else {
                db.editMatching(
                        Holding.class,
                        entity -> {
                            entity.setBorrowedQuantity(entity.getBorrowedQuantity() - (q));
                        },
                        eq("organizationId", org),
                        eq("securityId", security));
                db.create(
                        ShortLot::new,
                        entity -> {
                            entity.setOrganizationId(org);
                            entity.setSecurityId(security);
                            entity.setOrderId(id);
                            entity.setOpenQuantity(q);
                            entity.setPrice(quote.value());
                        });
            }
        }
        db.create(
                TradeHistory::new,
                entity -> {
                    entity.setOrganizationId(org);
                    entity.setOrderId(id);
                    entity.setSecurityId(security);
                    entity.setSide((String) order.get("side"));
                    entity.setSource((String) order.get("source"));
                    entity.setQuantity(q);
                    entity.setPrice(quote.value());
                    entity.setValue(value);
                    entity.setPriceAt(quote.at());
                });
        db.edit(
                TradeOrder.class,
                id,
                entity -> {
                    entity.setStatus("FILLED");
                    entity.setFilledAt(Instant.now());
                });
        events.write(
                org,
                "ORDER_FILLED",
                id,
                Map.of("executionPrice", quote.value(), "quantity", q, "simulated", true));
    }

    private long shortQuantity(long org, long security) {
        return db.sum(
                ShortLot.class,
                "openQuantity",
                Long.class,
                eq("organizationId", org),
                eq("securityId", security));
    }

    private void cover(long org, long security, long q, BigDecimal price) {
        require(
                shortQuantity(org, security) >= q,
                "Short position is no longer available to cover");
        long remaining = q;
        BigDecimal pnl = BigDecimal.ZERO;
        for (var lot :
                db.rows(
                        ShortLot.class,
                        Sort.by(Sort.Order.asc("id")),
                        Integer.MAX_VALUE,
                        eq("organizationId", org),
                        eq("securityId", security),
                        greaterThan("openQuantity", 0L))) {
            long fill = Math.min(remaining, n(lot, "open_quantity"));
            pnl = pnl.add(money(lot, "price").subtract(price).multiply(BigDecimal.valueOf(fill)));
            db.edit(
                    ShortLot.class,
                    n(lot, "id"),
                    entity -> {
                        entity.setOpenQuantity(entity.getOpenQuantity() - (fill));
                    });
            remaining -= fill;
            if (remaining == 0) break;
        }
        var holding = db.find(Holding.class, new HoldingId(org, security));
        holding.setRealizedPnl(holding.getRealizedPnl().add(amount(pnl)));
    }
}
