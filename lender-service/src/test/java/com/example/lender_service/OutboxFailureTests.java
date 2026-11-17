package com.example.lender_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import com.example.lender_service.service.OutboxService;
import java.time.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class OutboxFailureTests {

  @Test
  void brokerFailurePersistsFailedStateAndNotifiesOnce() {
    var db = mock(OutboxEventRepository.class);
    var notes = mock(NotificationRepository.class);
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    var clock = Clock.fixed(
      Instant.parse("2026-11-17T00:00:00Z"),
      ZoneOffset.UTC
    );
    var service = new OutboxService(
      db,
      kafka,
      mock(SimpMessagingTemplate.class),
      clock,
      true
    );
    ReflectionTestUtils.setField(service, "maxAttempts", 2);
    ReflectionTestUtils.setField(service, "notifications", notes);
    var event = new OutboxEvent();
    event.setAttempts(1);
    event.setWsSent(true);
    event.setPublished(false);
    event.setTopic("commands");
    event.setOrganizationId(7L);
    event.setEventId("original-id");
    event.setPayload("original");
    event.setPartitionKey("7");
    event.setNextAttemptAt(Instant.EPOCH);
    when(db.lockHead(eq("7"), eq(true), any())).thenReturn(List.of(event));
    when(kafka.send("commands", "7", "original")).thenReturn(
      CompletableFuture.failedFuture(new IllegalStateException("broker down"))
    );
    service.publishNext("7");
    assertEquals("FAILED", event.getStatus());
    assertEquals(2, event.getAttempts());
    service.publishNext("7");
    verify(notes, times(1)).save(any(Notification.class));
    verify(kafka, times(1)).send("commands", "7", "original");
  }
}
