package com.example.borrower_service.service;

import com.example.borrower_service.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;

@Component
@ConditionalOnProperty(name="borrower.kafka-enabled",havingValue="true")
public class KafkaInboundService {
    private static final Logger log = LoggerFactory.getLogger(KafkaInboundService.class);
    private final BorrowerEventProjectionService projections;private final MarketService market;private final JsonMapper json=JsonMapper.builder().build();
    public KafkaInboundService(BorrowerEventProjectionService projections,MarketService market) {this.projections=projections;this.market=market;}
    @KafkaListener(topics="${borrower.confirmations-topic}")
    public void confirmation(String body) {
        var event = json.readValue(body, BorrowerEvent.class);
        log.info("Borrower confirmation received: type={}, organizationId={}, referenceId={}, version={}", event.type(), event.organizationId(), event.referenceId(), event.version());
        try {
            var result = projections.apply(event);
            log.info("Borrower confirmation applied: type={}, result={}", event.type(), result);
        } catch (Exception e) {
            log.error("Borrower confirmation failed for type={}, organizationId={}, referenceId={}: {}", event.type(), event.organizationId(), event.referenceId(), e.getMessage(), e);
            throw e;
        }
    }
    @KafkaListener(topics="${borrower.market-events-topic}",groupId="borrower-market-prices")
    public void price(String body) {
        var node=json.readTree(body);
        long id=node.required("securityId").asLong();
        var price=new BigDecimal(node.required("close").asText());
        if(price.signum()<=0)throw new IllegalArgumentException("Market price must be positive");
        market.quote(id,new QuoteRequest(price,Instant.parse(node.required("tradeDateTimestamp").asText())));
    }
}
