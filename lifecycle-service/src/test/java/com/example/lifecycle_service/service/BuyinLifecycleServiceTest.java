package com.example.lifecycle_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class BuyinLifecycleServiceTest {

  LoanRepository loans = mock(LoanRepository.class);
  DefaultCaseRecordRepository cases = mock(DefaultCaseRecordRepository.class);
  RecallRequestRecordRepository requests = mock(
    RecallRequestRecordRepository.class
  );
  LenderOutboxEventRepository outbox = mock(LenderOutboxEventRepository.class);
  DefaultLifecycleService defaults = mock(DefaultLifecycleService.class);
  BuyinLifecycleService service = new BuyinLifecycleService(
    loans,
    cases,
    requests,
    outbox,
    defaults,
    new ObjectMapper()
  );
  DefaultCaseRecord c = new DefaultCaseRecord();

  @BeforeEach
  void setup() {
    when(loans.lockOrganization(1L)).thenReturn(mock(Organization.class));
    c.setId(99L);
    c.setLoanId(7L);
    c.setStatus("OPEN");
    c.setObligationType("RECALL");
    c.setQuantity(20L);
    when(cases.findByIdAndOrganizationId(99L, 1L)).thenReturn(Optional.of(c));
  }

  RecallRequestRecord request(
    long id,
    String type,
    String status,
    String payload
  ) {
    var r = mock(RecallRequestRecord.class);
    when(r.getType()).thenReturn(type);
    when(r.getStatus()).thenReturn(status);
    when(r.getLoanId()).thenReturn(7L);
    when(r.getPayload()).thenReturn(payload);
    when(requests.findByIdAndOrganizationId(id, 1L)).thenReturn(Optional.of(r));
    return r;
  }

  @Test
  void buyinAcceptanceRequiresCurrentSecuritiesDefaultAndRedeliveryIsIdempotent() throws Exception {
    request(10, "BUY_IN_REQUESTED", "PENDING", "{\"defaultReferenceId\":99}");
    when(outbox.existsByEventId("buyin-command-10")).thenReturn(false, true);
    service.process(1, 10);
    service.process(1, 10);
    verify(defaults).evaluate(1, 7);
    verify(defaults).emit(
      eq(1L),
      eq(10L),
      eq(1L),
      eq("buyin-command-10"),
      eq("LIFECYCLE_REQUEST_ACCEPTED"),
      anyMap()
    );
  }

  @Test
  void curedOrMonetaryDefaultRejectsInsteadOfSimulatingAnExecution() throws Exception {
    request(10, "BUY_IN_REQUESTED", "PENDING", "{\"defaultReferenceId\":99}");
    c.setObligationType("MARGIN_CALL");
    service.process(1, 10);
    verify(defaults).emit(
      eq(1L),
      eq(10L),
      eq(1L),
      anyString(),
      eq("LIFECYCLE_REQUEST_REJECTED"),
      anyMap()
    );
  }

  @Test
  void tradeReportCarriesRealReferenceAndLeavesSettlementUnperformed() throws Exception {
    request(10, "BUY_IN_REQUESTED", "ACCEPTED", "{\"defaultReferenceId\":99}");
    request(
      11,
      "BUY_IN_EXECUTION_REQUESTED",
      "PENDING",
      "{\"defaultReferenceId\":99,\"buyinRequestId\":10,\"quantity\":20,\"price\":101,\"costs\":5,\"executionReference\":\"TRADE-42\"}"
    );
    service.process(1, 11);
    @SuppressWarnings("unchecked")
    var data = ArgumentCaptor.forClass(Map.class);
    verify(defaults).emit(
      eq(1L),
      eq(2000000000011L),
      eq(1L),
      eq("buyin-command-11"),
      eq("BUY_IN_REPORTED"),
      data.capture()
    );
    assertEquals("TRADE-42", data.getValue().get("executionReference"));
    assertEquals("EXECUTED", data.getValue().get("status"));
    assertEquals("0", data.getValue().get("collateralApplied").toString());
    assertEquals("2025", data.getValue().get("residualBalance").toString());
  }

  @Test
  void changedOutstandingQuantityRejectsStaleExecution() throws Exception {
    request(10, "BUY_IN_REQUESTED", "ACCEPTED", "{\"defaultReferenceId\":99}");
    request(
      11,
      "BUY_IN_EXECUTION_REQUESTED",
      "PENDING",
      "{\"defaultReferenceId\":99,\"buyinRequestId\":10,\"quantity\":20,\"price\":101,\"costs\":0,\"executionReference\":\"TRADE-42\"}"
    );
    c.setQuantity(10L);
    service.process(1, 11);
    verify(defaults).emit(
      eq(1L),
      eq(11L),
      eq(1L),
      anyString(),
      eq("LIFECYCLE_REQUEST_REJECTED"),
      anyMap()
    );
  }
}
