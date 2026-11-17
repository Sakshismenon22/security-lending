package com.example.borrower_service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.borrower_service.model.*;
import com.example.borrower_service.service.DeliveryRecoveryService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class DeliveryRecoveryTests {

  @Test
  void retryRetainsEventAndPayloadAndResetsOnlyDeliveryState() {
    var em = mock(EntityManager.class);
    var clock = Clock.fixed(
      Instant.parse("2026-11-17T00:00:00Z"),
      ZoneOffset.UTC
    );
    var service = new DeliveryRecoveryService(em, clock);
    var failure = new DeliveryFailure();
    failure.setId("OUTBOX:kafka:3");
    failure.setOrganizationId(7L);
    failure.setStatus("FAILED");
    failure.setSource("OUTBOX");
    failure.setOutboxId(3L);
    var event = new OutboxEvent();
    event.setStatus("FAILED");
    event.setAttempts(20);
    event.setPayload("original");
    when(
      em.find(
        DeliveryFailure.class,
        failure.getId(),
        LockModeType.PESSIMISTIC_WRITE
      )
    ).thenReturn(failure);
    when(
      em.find(OutboxEvent.class, 3L, LockModeType.PESSIMISTIC_WRITE)
    ).thenReturn(event);
    service.decide(7L, failure.getId(), true);
    assertEquals("RETRYING", failure.getStatus());
    assertEquals("PENDING", event.getStatus());
    assertEquals(0, event.getAttempts());
    assertEquals("original", event.getPayload());
    assertEquals(clock.instant(), event.getNextAttemptAt());
  }

  @Test
  void closeRetainsFailureRecordAndOriginalPayloadWithoutRepublishing() {
    var em = mock(EntityManager.class);
    var service = new DeliveryRecoveryService(em, Clock.systemUTC());
    var failure = new DeliveryFailure();
    failure.setId("OUTBOX:kafka:3");
    failure.setOrganizationId(7L);
    failure.setSource("OUTBOX");
    failure.setOutboxId(3L);
    failure.setStatus("FAILED");
    var event = new OutboxEvent();
    event.setStatus("FAILED");
    event.setPayload("unchanged");
    event.setAttempts(20);
    when(
      em.find(
        DeliveryFailure.class,
        failure.getId(),
        LockModeType.PESSIMISTIC_WRITE
      )
    ).thenReturn(failure);
    when(
      em.find(OutboxEvent.class, 3L, LockModeType.PESSIMISTIC_WRITE)
    ).thenReturn(event);
    service.decide(7L, failure.getId(), false);
    assertEquals("CLOSED", failure.getStatus());
    assertEquals("CLOSED", event.getStatus());
    assertEquals("unchanged", event.getPayload());
    assertEquals(20, event.getAttempts());
    verify(em, never()).remove(any());
  }

  @Test
  void failureCannotBeChangedByAnotherOrganizationOrAfterItWasClosed() {
    var em = mock(EntityManager.class);
    var service = new DeliveryRecoveryService(em, Clock.systemUTC());
    var failure = new DeliveryFailure();
    failure.setId("test");
    failure.setOrganizationId(7L);
    failure.setStatus("CLOSED");
    when(
      em.find(DeliveryFailure.class, "test", LockModeType.PESSIMISTIC_WRITE)
    ).thenReturn(failure);
    assertThrows(ResponseStatusException.class, () ->
      service.decide(8L, "test", true)
    );
    assertThrows(ResponseStatusException.class, () ->
      service.decide(7L, "test", true)
    );
    verify(em, never()).find(
      OutboxEvent.class,
      3L,
      LockModeType.PESSIMISTIC_WRITE
    );
  }
}
