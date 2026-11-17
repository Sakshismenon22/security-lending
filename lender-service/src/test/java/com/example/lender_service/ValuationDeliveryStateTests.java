package com.example.lender_service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lender_service.model.OutboxEvent;
import com.example.lender_service.service.ValuationDeliveryState;
import jakarta.persistence.*;
import org.junit.jupiter.api.Test;

class ValuationDeliveryStateTests {

  @Test
  void commandAndResultFailuresUpdateTheSameBusinessRun() {
    for (boolean result : new boolean[] { false, true }) {
      var em = mock(EntityManager.class);
      var q = mock(Query.class, RETURNS_SELF);
      when(em.createQuery(anyString())).thenReturn(q);
      var event = new OutboxEvent();
      event.setOrganizationId(7L);
      event.setTopic(
        result ? "lender.lifecycle.events.v1" : "lender.lifecycle.commands.v1"
      );
      event.setPayload(
        result
          ? "{\"type\":\"VALUATION_REPORTED\",\"referenceId\":999,\"data\":{\"lifecycleRequestId\":9}}"
          : "{\"type\":\"VALUATION_REQUESTED\",\"referenceId\":9}"
      );
      ValuationDeliveryState.fail(em, event, "Kafka unavailable");
      verify(q).setParameter("id", 9L);
      verify(q).setParameter("org", 7L);
      verify(q).setParameter("reason", "Kafka unavailable");
      verify(q).executeUpdate();
      verify(em).createQuery(contains("r.status in ('PENDING','ACCEPTED')"));
    }
  }

  @Test
  void retryReopensOnlyTheFailedRunAndPreservesItsPayload() {
    var em = mock(EntityManager.class);
    var q = mock(Query.class, RETURNS_SELF);
    when(em.createQuery(anyString())).thenReturn(q);
    var event = new OutboxEvent();
    event.setOrganizationId(7L);
    event.setTopic("lender.lifecycle.commands.v1");
    event.setPayload("{\"type\":\"VALUATION_REQUESTED\",\"referenceId\":9}");
    ValuationDeliveryState.decide(em, event, true);
    verify(q).setParameter("status", "PENDING");
    verify(q).setParameter("reason", null);
    verify(em).createQuery(contains("r.status='FAILED'"));
  }

  @Test
  void aLateCompletedRunClearsItsTransportFailureInsteadOfBeingReopened() {
    var em = mock(EntityManager.class);
    var request = new com.example.lender_service.model.LifecycleRequest();
    request.setOrganizationId(7L);
    request.setType("VALUATION_REQUESTED");
    request.setStatus("COMPLETED");
    when(
      em.find(com.example.lender_service.model.LifecycleRequest.class, 9L)
    ).thenReturn(request);
    var event = new OutboxEvent();
    event.setOrganizationId(7L);
    event.setStatus("FAILED");
    event.setPublished(false);
    event.setPayload("{\"type\":\"VALUATION_REQUESTED\",\"referenceId\":9}");
    org.junit.jupiter.api.Assertions.assertTrue(
      ValuationDeliveryState.resolveTerminal(em, event)
    );
    org.junit.jupiter.api.Assertions.assertEquals(
      "PUBLISHED",
      event.getStatus()
    );
    org.junit.jupiter.api.Assertions.assertTrue(event.getPublished());
    org.junit.jupiter.api.Assertions.assertEquals(
      "COMPLETED",
      request.getStatus()
    );
  }
}
