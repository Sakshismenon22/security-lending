package com.example.lender_service.service;

import com.example.lender_service.model.*;
import jakarta.persistence.EntityManager;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** Reconciles buy-in command transport with its persisted request, never with custody balances. */
public final class BuyinDeliveryState {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private BuyinDeliveryState() {}

  private static Long requestId(OutboxEvent event) {
    if (
      event.getPayload() == null ||
      !event.getPayload().stripLeading().startsWith("{")
    ) return null;
    var root = JSON.readTree(event.getPayload());
    String type = root.path("type").asText();
    if (
      Set.of(
        "BUY_IN_REQUESTED",
        "BUY_IN_EXECUTION_REQUESTED",
        "LIFECYCLE_REQUEST_ACCEPTED",
        "LIFECYCLE_REQUEST_REJECTED"
      ).contains(type)
    ) return root.path("referenceId").asLong();
    if ("BUY_IN_REPORTED".equals(type)) return root
      .path("data")
      .path("executionRequestId")
      .asLong(root.path("data").path("lifecycleRequestId").asLong());
    return null;
  }

  public static boolean resolveTerminal(EntityManager em, OutboxEvent event) {
    Long id = requestId(event);
    if (id == null) return false;
    var request = em.find(LifecycleRequest.class, id);
    if (
      request == null ||
      !request.getOrganizationId().equals(event.getOrganizationId()) ||
      !Set.of("BUY_IN_REQUESTED", "BUY_IN_EXECUTION_REQUESTED").contains(
        request.getType()
      )
    ) return false;
    if (
      Set.of("COMPLETED", "REJECTED", "CLOSED").contains(request.getStatus())
    ) {
      boolean completed = "COMPLETED".equals(request.getStatus());
      event.setStatus(completed ? "PUBLISHED" : "CLOSED");
      if (completed) {
        event.setPublished(true);
        event.setLastError(null);
      }
      return true;
    }
    return false;
  }

  public static void fail(EntityManager em, OutboxEvent event, String reason) {
    Long id = requestId(event);
    if (id == null) return;
    String error =
      reason == null
        ? "Buy-in command delivery retries exhausted"
        : reason.substring(0, Math.min(1000, reason.length()));
    em.createQuery(
      "update LifecycleRequest r set r.status='FAILED',r.failureReason=:reason where r.organizationId=:org and r.id=:id and r.type in ('BUY_IN_REQUESTED','BUY_IN_EXECUTION_REQUESTED') and r.status in ('PENDING','ACCEPTED')"
    )
      .setParameter("org", event.getOrganizationId())
      .setParameter("id", id)
      .setParameter("reason", error)
      .executeUpdate();
  }

  public static void decide(
    EntityManager em,
    OutboxEvent event,
    boolean retry
  ) {
    Long id = requestId(event);
    if (id == null) return;
    em.createQuery(
      "update LifecycleRequest r set r.status=:status,r.failureReason=:reason where r.organizationId=:org and r.id=:id and r.type in ('BUY_IN_REQUESTED','BUY_IN_EXECUTION_REQUESTED') and r.status='FAILED'"
    )
      .setParameter("org", event.getOrganizationId())
      .setParameter("id", id)
      .setParameter("status", retry ? "PENDING" : "CLOSED")
      .setParameter("reason", retry ? null : "Buy-in delivery closed")
      .executeUpdate();
  }

  /** Retire exhausted predecessors so their failed partition head cannot block a new attempt. */
  public static void retireFailed(
    EntityManager em,
    long org,
    long loanId,
    long defaultReferenceId
  ) {
    var failed = em
      .createQuery(
        "select r from LifecycleRequest r where r.organizationId=:org and r.loanId=:loan and r.type in ('BUY_IN_REQUESTED','BUY_IN_EXECUTION_REQUESTED') and r.status='FAILED'",
        LifecycleRequest.class
      )
      .setParameter("org", org)
      .setParameter("loan", loanId)
      .getResultList();
    var ids = new HashSet<Long>();
    for (var request : failed) {
      if (
        JSON.readTree(request.getPayload())
          .path("defaultReferenceId")
          .asLong(-1) == defaultReferenceId
      ) {
        request.setStatus("CLOSED");
        ids.add(request.getId());
      }
    }
    if (ids.isEmpty()) return;
    for (var event : em
      .createQuery(
        "select e from OutboxEvent e where e.organizationId=:org and e.status='FAILED'",
        OutboxEvent.class
      )
      .setParameter("org", org)
      .getResultList()) {
      if (ids.contains(requestId(event))) {
        event.setStatus("CLOSED");
        em.createQuery(
          "update DeliveryFailure f set f.status='CLOSED' where f.organizationId=:org and f.eventId=:event and f.status in ('FAILED','RETRYING')"
        )
          .setParameter("org", org)
          .setParameter("event", event.getEventId())
          .executeUpdate();
      }
    }
  }
}
