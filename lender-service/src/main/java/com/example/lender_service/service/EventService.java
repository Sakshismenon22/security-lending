package com.example.lender_service.service;

import com.example.lender_service.model.*;
import com.example.lender_service.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * All outgoing borrower resource versions are assigned here, including relayed lifecycle decisions.
 */
@Service
@RequiredArgsConstructor
public class EventService {
  private final ConfirmationVersionRepository versions;
  private final OutboxEventRepository outbox;
  private final AuditEventRepository audit;
  private final NotificationRepository notifications;
  private final JsonService json;
  private final Clock clock;

  public void confirm(
      BorrowerConnection connection,
      String type,
      String resource,
      long borrowerReference,
      Object data) {
    var sequence =
        versions
            .findByConnectionIdAndResourceAndReferenceId(
                connection.getId(), resource, borrowerReference)
            .orElseGet(
                () -> {
                  var v = new ConfirmationVersion();
                  v.setConnectionId(connection.getId());
                  v.setResource(resource);
                  v.setReferenceId(borrowerReference);
                  v.setSequenceNumber(0L);
                  return v;
                });
    sequence.setSequenceNumber(sequence.getSequenceNumber() + 1);
    versions.save(sequence);
    var payload = new LinkedHashMap<String, Object>();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("type", type);
    payload.put("organizationId", connection.getBorrowerOrganizationId());
    payload.put("referenceId", borrowerReference);
    payload.put("version", sequence.getSequenceNumber());
    payload.put("occurredAt", clock.instant());
    payload.put("borrowerExternalId", connection.getExternalId());
    payload.put("data", data);
    store(
        connection.getOrganizationId(),
        "lender.confirmations.v1",
        String.valueOf(connection.getBorrowerOrganizationId()),
        payload);
    audit(connection.getOrganizationId(), type, String.valueOf(borrowerReference));
  }

  public void local(long org, String type, long reference, Object data) {
    internal(org, "lender.events.v1", type, reference, data);
  }

  public void lifecycle(long org, String type, long reference, Object data) {
    internal(org, "lender.lifecycle.commands.v1", type, reference, data);
  }

  private void internal(long org, String topic, String type, long reference, Object data) {
    store(
        org,
        topic,
        String.valueOf(org),
        Map.of(
            "eventId",
            UUID.randomUUID().toString(),
            "type",
            type,
            "organizationId",
            org,
            "referenceId",
            reference,
            "occurredAt",
            clock.instant(),
            "data",
            data));
    audit(org, type, String.valueOf(reference));
  }

  private void store(long org, String topic, String key, Map<String, Object> payload) {
    var e = new OutboxEvent();
    e.setOrganizationId(org);
    e.setEventId(payload.get("eventId").toString());
    e.setTopic(topic);
    e.setPartitionKey(key);
    e.setPayload(json.write(payload));
    e.setPublished(false);
    e.setWsSent(false);
    e.setAttempts(0);
    e.setNextAttemptAt(clock.instant());
    e.setCreatedAt(clock.instant());
    outbox.save(e);
  }

  public void audit(long org, String type, String reference) {
    var a = new AuditEvent();
    a.setOrganizationId(org);
    a.setType(type);
    a.setReference(reference);
    a.setCreatedAt(clock.instant());
    audit.save(a);
    var n = new Notification();
    n.setOrganizationId(org);
    n.setType(type);
    n.setReference(reference);
    n.setMessage(type.replace('_', ' '));
    n.setIsRead(false);
    n.setCreatedAt(clock.instant());
    notifications.save(n);
  }
}
