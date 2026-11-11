package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.dto.*;
import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class MarketService {
    private final BorrowerRepository db;
    private final RestClient client;
    private final long maxAge;

    public MarketService(
            BorrowerRepository db,
            RestClient marketDataClient,
            @Value("${borrower.max-price-age-seconds:900}") long maxAge) {
        this.db = db;
        this.maxAge = maxAge;
        client = marketDataClient;
    }

    @Transactional
    public Map<String, Object> register(SecurityRequest r) {
        if (db.rows(SecurityMaster.class, eq("id", r.id())).isEmpty())
            db.persist(
                    SecurityMaster::new,
                    entity -> {
                        entity.setId(r.id());
                        entity.setSymbol(r.symbol().toUpperCase(Locale.ROOT));
                        entity.setExchange(r.exchange().toUpperCase(Locale.ROOT));
                        entity.setName(r.name());
                        entity.setIsin(r.isin());
                        entity.setCurrency(r.currency());
                    });
        else
            db.edit(
                    SecurityMaster.class,
                    r.id(),
                    entity -> {
                        entity.setSymbol(r.symbol().toUpperCase(Locale.ROOT));
                        entity.setExchange(r.exchange().toUpperCase(Locale.ROOT));
                        entity.setName(r.name());
                        entity.setIsin(r.isin());
                        entity.setCurrency(r.currency());
                    });
        return db.row(SecurityMaster.class, r.id());
    }

    @Transactional
    public Map<String, Object> quote(long id, QuoteRequest quote) {
        db.lockedRow(SecurityMaster.class, id);
        require(!quote.priceAt().isAfter(Instant.now()), "Price timestamp cannot be in the future");
        var old = db.row(SecurityMaster.class, id);
        if (old.get("price_at") != null && quote.priceAt().isBefore(instant(old, "price_at")))
            return old;
        db.edit(
                SecurityMaster.class,
                id,
                entity -> {
                    entity.setPrice(quote.price());
                    entity.setPriceAt(quote.priceAt());
                });
        return db.row(SecurityMaster.class, id);
    }

    public MarketPrice price(long id) {
        var s = db.row(SecurityMaster.class, id);
        require(
                s.get("price") != null && s.get("price_at") != null,
                "No market price for security_master " + id);
        Instant at = instant(s, "price_at");
        // require(Duration.between(at,Instant.now()).getSeconds()<=maxAge,"Market price is stale
        // for security_master "+id+"; refresh before transacting");
        return new MarketPrice(money(s, "price"), at);
    }

    public List<Map<String, Object>> history(long id) {
        var s = db.row(SecurityMaster.class, id);
        List<Map<String, Object>> result =
                client.get()
                        .uri(
                                b ->
                                        b.path("/api/v1/security-data/history")
                                                .queryParam("symbol", s.get("symbol"))
                                                .queryParam("exchange", s.get("exchange"))
                                                .build())
                        .retrieve()
                        .body(new org.springframework.core.ParameterizedTypeReference<>() {});
        return result == null ? List.of() : result;
    }

    @Transactional
    public Map<String, Object> refresh(long id) {
        var latest =
                history(id).stream()
                        .filter(c -> c.get("close") != null && c.get("tradedDateTimestamp") != null)
                        .filter(
                                c ->
                                        Instant.parse(c.get("tradedDateTimestamp").toString())
                                                .isBefore(
                                                        Instant.now()
                                                                .truncatedTo(
                                                                        java.time.temporal
                                                                                .ChronoUnit
                                                                                .MINUTES)))
                        .max(
                                Comparator.comparing(
                                        c ->
                                                Instant.parse(
                                                        c.get("tradedDateTimestamp").toString())))
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.CONFLICT,
                                                "No completed candles available"));
        return quote(
                id,
                new QuoteRequest(
                        new BigDecimal(latest.get("close").toString()),
                        Instant.parse(latest.get("tradedDateTimestamp").toString())));
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public int importSecurities() {
        var response =
                client.get()
                        .uri("/api/v1/security-master/get-all-securities")
                        .retrieve()
                        .body(Map.class);
        require(
                response != null && response.get("data") instanceof List,
                "Unexpected market-data security_master response");
        var rows = (List<Map<String, Object>>) response.get("data");
        for (var r : rows) {
            String currency = String.valueOf(r.get("currency"));
            require("INR".equals(currency), "Only INR is supported in this demo");
            register(
                    new SecurityRequest(
                            ((Number) r.get("id")).longValue(),
                            String.valueOf(r.get("symbol")),
                            String.valueOf(r.get("exchange")),
                            String.valueOf(r.get("securityName")),
                            Objects.toString(r.getOrDefault("isin", r.get("ISIN")), null),
                            currency));
        }
        return rows.size();
    }
}
