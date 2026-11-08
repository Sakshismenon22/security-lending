package com.example.lender_service.config;

import com.example.lender_service.repository.SecurityMasterRepository;
import com.example.lender_service.service.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lender.jobs-enabled", havingValue = "true", matchIfMissing = true)
public class BackgroundJobs {
  private static final Logger log = LoggerFactory.getLogger(BackgroundJobs.class);
  private final OutboxService outbox;
  private final MarketService market;
  private final SecurityMasterRepository securities;

  @Scheduled(fixedDelayString = "${lender.outbox-poll-ms:1000}")
  public void publish() {
    for (var key : outbox.pending())
      try {
        outbox.publishNext(key);
      } catch (Exception e) {
        log.warn("Outbox partition {} remains pending: {}", key, e.getMessage());
      }
  }

  @Scheduled(fixedDelayString = "${lender.market-poll-ms:60000}")
  public void prices() {
    for (var s : securities.findAll())
      try {
        market.refresh(s.getId());
      } catch (Exception e) {
        log.warn("Market price refresh {} failed: {}", s.getId(), e.getMessage());
      }
  }
}
