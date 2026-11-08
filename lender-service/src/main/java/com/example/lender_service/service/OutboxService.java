package com.example.lender_service.service;

import com.example.lender_service.repository.OutboxEventRepository;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {
  private final OutboxEventRepository outbox;
  private final KafkaTemplate<String, String> kafka;
  private final SimpMessagingTemplate websocket;
  private final Clock clock;
  private final boolean enabled;

  public OutboxService(
      OutboxEventRepository outbox,
      KafkaTemplate<String, String> kafka,
      SimpMessagingTemplate websocket,
      Clock clock,
      @Value("${lender.kafka-enabled:false}") boolean enabled) {
    this.outbox = outbox;
    this.kafka = kafka;
    this.websocket = websocket;
    this.clock = clock;
    this.enabled = enabled;
  }

  public List<String> pending() {
    return outbox.pendingPartitions(enabled);
  }

  @Transactional
  public void publishNext(String key) {
    var pending = outbox.lockHead(key, enabled, PageRequest.of(0, 1));
    if (pending.isEmpty()) return;
    var event = pending.get(0);
    if (event.getNextAttemptAt().isAfter(clock.instant())) return;
    event.setAttempts(event.getAttempts() + 1);
    try {
      if (!event.getWsSent()) {
        websocket.convertAndSend(
            "/topic/organizations/" + event.getOrganizationId() + "/events", event.getPayload());
        event.setWsSent(true);
      }
      if (enabled && !event.getPublished()) {
        kafka
            .send(event.getTopic(), event.getPartitionKey(), event.getPayload())
            .get(20, TimeUnit.SECONDS);
        event.setPublished(true);
      }
      event.setLastError(null);
    } catch (Exception e) {
      String error = e.getClass().getSimpleName() + ": " + e.getMessage();
      event.setLastError(error.substring(0, Math.min(1000, error.length())));
      event.setNextAttemptAt(clock.instant().plusSeconds(5));
    }
  }
}
