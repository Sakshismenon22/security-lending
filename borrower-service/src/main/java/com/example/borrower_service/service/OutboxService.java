package com.example.borrower_service.service;

import static com.example.borrower_service.repository.BorrowerRepository.*;

import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

  private final BorrowerRepository db;

  @jakarta.persistence.PersistenceContext
  private jakarta.persistence.EntityManager entityManager;

  private final KafkaTemplate<String, String> kafka;
  private final SimpMessagingTemplate websocket;
  private final boolean kafkaEnabled;

  @Value("${borrower.outbox-max-attempts:20}")
  private int maxAttempts;

  @Value("${borrower.outbox-retry-seconds:5}")
  private int retrySeconds;

  private final String topic, commandsTopic;

  public OutboxService(
    BorrowerRepository db,
    KafkaTemplate<String, String> kafka,
    SimpMessagingTemplate websocket,
    @Value("${borrower.kafka-enabled:false}") boolean kafkaEnabled,
    @Value("${borrower.events-topic}") String topic,
    @Value("${borrower.commands-topic}") String commandsTopic
  ) {
    this.db = db;
    this.kafka = kafka;
    this.websocket = websocket;
    this.kafkaEnabled = kafkaEnabled;
    this.topic = topic;
    this.commandsTopic = commandsTopic;
  }

  public List<Map<String, Object>> pending() {
    return entityManager
      .createQuery(
        "select e from OutboxEvent e where e.status<>'CLOSED' and (e.wsSent=false or (:enabled=true and e.published=false)) " +
          "and not exists (select p.id from OutboxEvent p where p.organizationId=e.organizationId and p.id<e.id " +
          "and p.status<>'CLOSED' and (p.wsSent=false or (:enabled=true and p.published=false))) order by e.id",
        OutboxEvent.class
      )
      .setParameter("enabled", kafkaEnabled)
      .setMaxResults(100)
      .getResultList()
      .stream()
      .map(BorrowerRepository::columns)
      .toList();
  }

  @Transactional
  public void publish(long id) throws Exception {
    var row = db.lockedRow(OutboxEvent.class, id);
    var event = db.find(OutboxEvent.class, id);
    if (
      Set.of("FAILED", "CLOSED").contains(event.getStatus()) ||
      event.getNextAttemptAt().isAfter(java.time.Instant.now())
    ) return;
    long org = n(row, "organization_id");
    event.setAttempts(event.getAttempts() + 1);
    try {
      String payload = row.get("payload").toString();
      if (!Boolean.TRUE.equals(row.get("ws_sent"))) {
        websocket.convertAndSend(
          "/topic/organizations/" + org + "/events",
          payload
        );
        db.edit(OutboxEvent.class, id, entity -> {
          entity.setWsSent(true);
        });
      }
      if (kafkaEnabled && !Boolean.TRUE.equals(row.get("published"))) {
        kafka
          .send(
            isCommand(row.get("event_type").toString()) ? commandsTopic : topic,
            String.valueOf(org),
            payload
          )
          .get(10, TimeUnit.SECONDS);
        db.edit(OutboxEvent.class, id, entity -> {
          entity.setPublished(true);
        });
      }
      event.setLastError(null);
      if (Boolean.TRUE.equals(event.getPublished())) event.setStatus(
        "PUBLISHED"
      );
    } catch (Exception failure) {
      String error =
        failure.getClass().getSimpleName() + ": " + failure.getMessage();
      event.setLastError(error.substring(0, Math.min(1000, error.length())));
      event.setNextAttemptAt(java.time.Instant.now().plusSeconds(retrySeconds));
      if (event.getAttempts() >= maxAttempts) {
        event.setStatus("FAILED");
        db.create(Notification::new, n -> {
          n.setOrganizationId(org);
          n.setType("REQUEST_DELIVERY_FAILED");
          n.setReference(String.valueOf(id));
          n.setMessage(
            event.getEventType() +
              " delivery failed after " +
              event.getAttempts() +
              " attempts. Open Failed deliveries to retry or close."
          );
        });
      }
      if (
        failure instanceof InterruptedException
      ) Thread.currentThread().interrupt();
    }
  }

  private boolean isCommand(String type) {
    // A fee payment is a borrower-to-lender settlement instruction, even though its
    // lifecycle name does not end in _REQUESTED.  It must therefore pass through the
    // borrower FIX adapter on borrower.commands.v1, not the local events stream.
    return (
      type.endsWith("_REQUESTED") ||
      type.equals("AGREEMENT_ACCEPTED") ||
      type.equals("AGREEMENT_REJECTED") ||
      type.equals("FEE_PAYMENT_RECEIVED")
    );
  }
}
