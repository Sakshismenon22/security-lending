package com.example.borrower_service.service;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.dto.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Service
public class MarketService {
    private final BorrowerRepository db; private final RestClient client; private final long maxAge;
    public MarketService(BorrowerRepository db,RestClient marketDataClient,
                         @Value("${borrower.max-price-age-seconds:900}") long maxAge) {
        this.db=db; this.maxAge=maxAge;
        client=marketDataClient;
    }
    @Transactional
    public Map<String,Object> register(SecurityRequest r) {
        if(db.list("select id from security_master where id=?",r.id()).isEmpty())
            db.update("insert into security_master(id,symbol,exchange,name,isin,currency) values (?,?,?,?,?,?)",r.id(),r.symbol().toUpperCase(Locale.ROOT),r.exchange().toUpperCase(Locale.ROOT),r.name(),r.isin(),r.currency());
        else db.update("update security_master set symbol=?,exchange=?,name=?,isin=?,currency=? where id=?",r.symbol().toUpperCase(Locale.ROOT),r.exchange().toUpperCase(Locale.ROOT),r.name(),r.isin(),r.currency(),r.id());
        return db.one("select * from security_master where id=?",r.id());
    }
    @Transactional
    public Map<String,Object> quote(long id,QuoteRequest quote) {
        db.one("select id from security_master where id=? for update",id);
        require(!quote.priceAt().isAfter(Instant.now()),"Price timestamp cannot be in the future");
        var old=db.one("select * from security_master where id=?",id);
        if(old.get("price_at")!=null && quote.priceAt().isBefore(instant(old,"price_at")))return old;
        db.update("update security_master set price=?,price_at=? where id=?",quote.price(),OffsetDateTime.ofInstant(quote.priceAt(),ZoneOffset.UTC),id);
        return db.one("select * from security_master where id=?",id);
    }
    public MarketPrice price(long id) {
        var s=db.one("select * from security_master where id=?",id);
        require(s.get("price")!=null && s.get("price_at")!=null,"No market price for security_master "+id);
        Instant at=instant(s,"price_at");
        // require(Duration.between(at,Instant.now()).getSeconds()<=maxAge,"Market price is stale for security_master "+id+"; refresh before transacting");
        return new MarketPrice(money(s,"price"),at);
    }
    public List<Map<String,Object>> history(long id) {
        var s=db.one("select * from security_master where id=?",id);
        List<Map<String,Object>> result=client.get().uri(b->b.path("/api/v1/security-data/history").queryParam("symbol",s.get("symbol")).queryParam("exchange",s.get("exchange")).build()).retrieve().body(new org.springframework.core.ParameterizedTypeReference<>(){});
        return result==null?List.of():result;
    }
    @Transactional
    public Map<String,Object> refresh(long id) {
        var latest=history(id).stream().filter(c->c.get("close")!=null && c.get("tradedDateTimestamp")!=null)
            .filter(c->Instant.parse(c.get("tradedDateTimestamp").toString()).isBefore(Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES)))
            .max(Comparator.comparing(c->Instant.parse(c.get("tradedDateTimestamp").toString()))).orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"No completed candles available"));
        return quote(id,new QuoteRequest(new BigDecimal(latest.get("close").toString()),Instant.parse(latest.get("tradedDateTimestamp").toString())));
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public int importSecurities() {
        var response=client.get().uri("/api/v1/security-master/get-all-securities").retrieve().body(Map.class);
        require(response!=null && response.get("data") instanceof List,"Unexpected market-data security_master response");
        var rows=(List<Map<String,Object>>)response.get("data");
        for(var r:rows) {
            String currency=String.valueOf(r.get("currency")); require("INR".equals(currency),"Only INR is supported in this demo");
            register(new SecurityRequest(((Number)r.get("id")).longValue(),String.valueOf(r.get("symbol")),String.valueOf(r.get("exchange")),String.valueOf(r.get("securityName")),Objects.toString(r.getOrDefault("isin",r.get("ISIN")),null),currency));
        }
        return rows.size();
    }
}
