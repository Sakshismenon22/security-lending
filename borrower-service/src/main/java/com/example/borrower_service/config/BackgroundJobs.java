package com.example.borrower_service.config;

import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.service.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import static com.example.borrower_service.repository.BorrowerRepository.*;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="borrower.jobs-enabled",havingValue="true",matchIfMissing=true)
public class BackgroundJobs {
    private static final Logger log=LoggerFactory.getLogger(BackgroundJobs.class);
    private final BorrowerRepository db;private final TradingService trading;private final MarketService market;
    private final OutboxService outbox;
    @Scheduled(fixedDelayString="${borrower.outbox-poll-ms:2000}")
    public void outbox() {
        for(var r:outbox.pending())try{outbox.publish(n(r,"id"));}catch(Exception e){log.warn("Outbox event {} remains pending: {}",r.get("id"),e.getMessage());}
    }
    @Scheduled(fixedDelayString="${borrower.market-poll-ms:60000}")
    public void prices() {
        for(var s:db.list("select id from security_master order by id"))try{market.refresh(n(s,"id"));}catch(Exception e){log.warn("Market refresh {} failed: {}",s.get("id"),e.getMessage());}
    }
    @Scheduled(fixedDelayString="${borrower.order-poll-ms:5000}")
    public void orders() {
        for(var r:db.list("select id,organization_id from trade_order where status='PENDING' order by id"))try{trading.match(n(r,"organization_id"),n(r,"id"));}catch(Exception e){log.warn("Order {} remains pending: {}",r.get("id"),e.getMessage());}
    }
}
