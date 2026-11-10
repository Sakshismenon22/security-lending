package com.example.lifecycle_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RecallLifecycleServiceTest {
  @Mock LoanRepository loans;
  @Mock MslaRepository agreements;
  @Mock RecallRequestRecordRepository requests;
  @Mock RecallRecordRepository recalls;
  @Mock LenderOutboxEventRepository outbox;
  @Spy ObjectMapper json = new ObjectMapper();
  @InjectMocks RecallLifecycleService service;

  @BeforeEach
  void setTopic() {
    ReflectionTestUtils.setField(service, "topic", "lender.lifecycle.events.v1");
  }

  @Test
  void issuanceUsesMslaDeadlineAndQueuesOnlyOneReportOnRedelivery() throws Exception {
    var request = mock(RecallRequestRecord.class);
    when(request.getType()).thenReturn("RECALL_REQUESTED");
    when(request.getStatus()).thenReturn("PENDING");
    when(request.getLoanId()).thenReturn(7L);
    when(request.getConnectionId()).thenReturn(3L);
    when(request.getPayload()).thenReturn("{\"quantity\":30,\"reason\":\"Need shares\"}");
    var loan = new Loan();
    loan.setId(7L);
    loan.setConnectionId(3L);
    loan.setMslaId(5L);
    loan.setStatus("ACTIVE");
    loan.setOutstandingQuantity(100L);
    var agreement = new Msla();
    agreement.setRecallAllowed(true);
    agreement.setRecallHours(48);
    when(loans.lockOrganization(1L)).thenReturn(mock(Organization.class));
    when(requests.findByIdAndOrganizationId(42L, 1L)).thenReturn(Optional.of(request));
    when(loans.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(loan));
    when(agreements.findById(5L)).thenReturn(Optional.of(agreement));
    when(outbox.existsByEventId("recall-issue-42")).thenReturn(false, true);

    service.issue(1, 42);
    service.issue(1, 42);

    var capture = ArgumentCaptor.forClass(LenderOutboxEvent.class);
    verify(outbox).save(capture.capture());
    var report = json.readTree(capture.getValue().getPayload());
    assertEquals("RECALL_UPDATED", report.path("type").asText());
    assertEquals(42, report.path("referenceId").asLong());
    assertEquals(42, report.path("data").path("lifecycleRequestId").asLong());
    assertEquals("OPEN", report.path("data").path("status").asText());
    assertEquals(30, report.path("data").path("quantity").asLong());
    assertEquals(
        Duration.ofHours(48),
        Duration.between(
            Instant.parse(report.path("occurredAt").asText()),
            Instant.parse(report.path("data").path("dueAt").asText())));
  }

  @Test
  void deadlineReportIsDeduplicatedAndSkipsFulfilledRecalls() throws Exception {
    var recall = mock(RecallRecord.class);
    var dueAt = Instant.now().minusSeconds(60);
    when(recall.getOrganizationId()).thenReturn(1L);
    when(recall.getDueAt()).thenReturn(dueAt);
    when(recall.getStatus()).thenReturn("PARTIALLY_FULFILLED", "PARTIALLY_FULFILLED", "FULFILLED");
    when(recall.getQuantity()).thenReturn(30L);
    when(recall.getFulfilledQuantity()).thenReturn(10L);
    when(recall.getOwnerReferenceId()).thenReturn(42L);
    when(loans.lockOrganization(1L)).thenReturn(mock(Organization.class));
    when(recalls.findById(4L)).thenReturn(Optional.of(recall));
    when(outbox.existsByEventId(startsWith("recall-due-1-4-"))).thenReturn(false, true);

    service.reportDeadline(1, 4);
    service.reportDeadline(1, 4);
    service.reportDeadline(1, 4);

    var capture = ArgumentCaptor.forClass(LenderOutboxEvent.class);
    verify(outbox).save(capture.capture());
    var report = json.readTree(capture.getValue().getPayload());
    assertEquals("RECALL_DEADLINE_REACHED", report.path("type").asText());
    assertEquals(42, report.path("referenceId").asLong());
    assertEquals(dueAt.toString(), report.path("data").path("expectedDueAt").asText());
  }
}
