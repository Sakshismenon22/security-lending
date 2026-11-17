package com.example.borrower_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.borrower_service.model.*;
import com.example.borrower_service.repository.BorrowerRepository;
import com.example.borrower_service.service.OutboxService;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxFailureTests {

  @Test
  void brokerFailureCommitsAttemptAndEventuallyFailsInsteadOfRetryingForever() throws Exception {
    var db = mock(BorrowerRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var service = new OutboxService(
      db,
      kafka,
      mock(SimpMessagingTemplate.class),
      true,
      "events",
      "commands"
    );
    ReflectionTestUtils.setField(
      service,
      "entityManager",
      mock(jakarta.persistence.EntityManager.class, RETURNS_DEEP_STUBS)
    );
    ReflectionTestUtils.setField(service, "maxAttempts", 2);
    ReflectionTestUtils.setField(service, "retrySeconds", 5);
    var event = new OutboxEvent();
    event.setAttempts(1);
    event.setStatus("PENDING");
    event.setEventType("LOCATE_REQUESTED");
    when(db.lockedRow(OutboxEvent.class, 1L)).thenReturn(
      Map.of(
        "organization_id",
        7L,
        "payload",
        "original",
        "event_type",
        "LOCATE_REQUESTED",
        "ws_sent",
        true,
        "published",
        false
      )
    );
    when(db.find(OutboxEvent.class, 1L)).thenReturn(event);
    when(kafka.send("commands", "7", "original")).thenReturn(
      CompletableFuture.failedFuture(new IllegalStateException("broker down"))
    );
    service.publish(1L);
    assertEquals(2, event.getAttempts());
    assertEquals("FAILED", event.getStatus());
    assertNotNull(event.getLastError());
    service.publish(1L);
    verify(kafka, times(1)).send("commands", "7", "original");
  }

  @Test
  void finalReturnSendFailureMarksTheActualRequestFailedWithoutReleasingAssets() throws Exception {
    var db = mock(BorrowerRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var service = new OutboxService(
      db,
      kafka,
      mock(SimpMessagingTemplate.class),
      true,
      "events",
      "commands"
    );
    var em = mock(jakarta.persistence.EntityManager.class);
    var update = mock(jakarta.persistence.Query.class, RETURNS_SELF);
    when(em.createQuery(anyString())).thenReturn(update);
    ReflectionTestUtils.setField(service, "entityManager", em);
    ReflectionTestUtils.setField(service, "maxAttempts", 1);
    ReflectionTestUtils.setField(service, "retrySeconds", 5);
    String payload = "{\"eventId\":\"original-return\",\"referenceId\":9}";
    var event = new OutboxEvent();
    event.setAttempts(0);
    event.setStatus("PENDING");
    event.setEventType("RETURN_REQUESTED");
    event.setPayload(payload);
    when(db.lockedRow(OutboxEvent.class, 1L)).thenReturn(
      Map.of(
        "organization_id",
        7L,
        "payload",
        payload,
        "event_type",
        "RETURN_REQUESTED",
        "ws_sent",
        true,
        "published",
        false
      )
    );
    when(db.find(OutboxEvent.class, 1L)).thenReturn(event);
    when(kafka.send("commands", "7", payload)).thenReturn(
      CompletableFuture.failedFuture(new IllegalStateException("broker down"))
    );
    service.publish(1L);
    assertEquals("FAILED", event.getStatus());
    verify(update).setParameter("event", "original-return");
    verify(update).setParameter("id", 9L);
    verify(update).setParameter("org", 7L);
    verify(update).executeUpdate();
    verify(db, never()).find(eq(Holding.class), any());
  }
}
