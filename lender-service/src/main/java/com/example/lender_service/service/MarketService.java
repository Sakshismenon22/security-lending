package com.example.lender_service.service;

import static com.example.lender_service.service.Checks.*;

import com.example.lender_service.dto.*;
import com.example.lender_service.model.SecurityMaster;
import com.example.lender_service.repository.SecurityMasterRepository;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

@Service
public class MarketService {
  private final SecurityMasterRepository securities;
  private final RestClient client;
  private final Clock clock;
  private final long maxAge;

  public MarketService(
      SecurityMasterRepository securities,
      RestClient marketDataClient,
      Clock clock,
      @Value("${lender.max-price-age-seconds:900}") long maxAge) {
    this.securities = securities;
    this.client = marketDataClient;
    this.clock = clock;
    this.maxAge = maxAge;
  }

  @Transactional
  public SecurityMaster register(SecurityRequest r) {
    var s = securities.findById(r.id()).orElseGet(SecurityMaster::new);
    s.setId(r.id());
    s.setSymbol(r.symbol().toUpperCase(Locale.ROOT));
    s.setExchange(r.exchange().toUpperCase(Locale.ROOT));
    s.setName(r.name());
    s.setIsin(r.isin());
    s.setCurrency(r.currency());
    return securities.save(s);
  }

  @Transactional
  public int importSecurities() {
    JsonNode response =
        client
            .get()
            .uri("/api/v1/security-master/get-all-securities")
            .retrieve()
            .body(JsonNode.class);
    require(
        response != null && response.has("data") && response.get("data").isArray(),
        "Unexpected market-data security response");
    int count = 0;
    for (var r : response.get("data")) {
      String currency = r.path("currency").asText();
      require("INR".equals(currency), "Only INR is supported");
      register(
          new SecurityRequest(
              r.path("id").asLong(),
              r.path("symbol").asText(),
              r.path("exchange").asText(),
              r.path("securityName").asText(),
              r.path("isin").asText(r.path("ISIN").asText(null)),
              currency));
      count++;
    }
    return count;
  }

  public JsonNode history(long id) {
    var s = found(securities.findById(id));
    return client
        .get()
        .uri(
            b ->
                b.path("/api/v1/security-data/history")
                    .queryParam("symbol", s.getSymbol())
                    .queryParam("exchange", s.getExchange())
                    .build())
        .retrieve()
        .body(JsonNode.class);
  }

  @Transactional
  public SecurityMaster refresh(long id) {
    JsonNode rows = history(id);
    require(rows != null && rows.isArray(), "Unexpected candle response");
    Instant latest = null;
    BigDecimal price = null;
    for (var r : rows) {
      if (!r.hasNonNull("close") || !r.hasNonNull("tradedDateTimestamp")) continue;
      Instant at = Instant.parse(r.get("tradedDateTimestamp").asText());
      if (at.isBefore(clock.instant().truncatedTo(ChronoUnit.MINUTES))
          && (latest == null || at.isAfter(latest))) {
        latest = at;
        price = r.get("close").decimalValue();
      }
    }
    require(latest != null, "No completed candles available");
    return quote(id, price, latest);
  }

  @Transactional
  public SecurityMaster quote(long id, BigDecimal value, Instant at) {
    var s = found(securities.findById(id));
    require(value.signum() > 0 && !at.isAfter(clock.instant()), "Invalid market quote");
    if (s.getPriceAt() == null || !at.isBefore(s.getPriceAt())) {
      s.setPrice(value);
      s.setPriceAt(at);
    }
    return s;
  }

  public MarketPrice price(long id) {
    var s = found(securities.findById(id));
    require(s.getPrice() != null && s.getPriceAt() != null, "No cached price for security " + id);
    require(
        Duration.between(s.getPriceAt(), clock.instant()).getSeconds() <= maxAge,
        "Stale price for security " + id);
    return new MarketPrice(s.getPrice(), s.getPriceAt());
  }
}
