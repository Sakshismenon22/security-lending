package com.example.lifecycle_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;

class DefaultLifecycleServiceTest {

  LoanRepository loans = mock(LoanRepository.class);
  RecallRecordRepository recalls = mock(RecallRecordRepository.class);
  MarginCallRepository margins = mock(MarginCallRepository.class);
  DefaultCaseRecordRepository cases = mock(DefaultCaseRecordRepository.class);
  LenderOutboxEventRepository outbox = mock(LenderOutboxEventRepository.class);
  ObjectMapper json = new ObjectMapper();
  Instant now = Instant.parse("2026-11-18T12:00:00Z");
  List<DefaultCaseRecord> stored = new ArrayList<>();
  List<JsonNode> reports = new ArrayList<>();
  Loan loan = new Loan();
  DefaultLifecycleService service;

  @BeforeEach
  void setup() throws Exception {
    service = new DefaultLifecycleService(
      loans,
      recalls,
      margins,
      cases,
      outbox,
      json,
      Clock.fixed(now, ZoneOffset.UTC)
    );
    ReflectionTestUtils.setField(
      service,
      "topic",
      "lender.lifecycle.events.v1"
    );
    loan.setId(7L);
    loan.setOrganizationId(1L);
    loan.setStatus("ACTIVE");
    loan.setOutstandingQuantity(100L);
    loan.setSettledAt(now.minusSeconds(100));
    when(loans.lockOrganization(1L)).thenReturn(mock(Organization.class));
    when(loans.findByIdAndOrganizationId(7L, 1L)).thenReturn(Optional.of(loan));
    when(cases.findByOrganizationIdAndLoanId(1L, 7L)).thenAnswer(x ->
      new ArrayList<>(stored)
    );
    when(cases.saveAndFlush(any())).thenAnswer(x -> {
      DefaultCaseRecord c = x.getArgument(0);
      if (c.getId() == null) {
        c.setId(1000000000000L + stored.size());
        stored.add(c);
      }
      return c;
    });
    when(outbox.save(any())).thenAnswer(x -> {
      LenderOutboxEvent e = x.getArgument(0);
      reports.add(json.readTree(e.getPayload()));
      return e;
    });
  }

  RecallRecord recall(Instant due, long quantity, long filled) {
    var r = mock(RecallRecord.class);
    when(r.getOrganizationId()).thenReturn(1L);
    when(r.getOwnerReferenceId()).thenReturn(42L);
    when(r.getDueAt()).thenReturn(due);
    when(r.getStatus()).thenReturn("OVERDUE");
    when(r.getQuantity()).thenReturn(quantity);
    when(r.getFulfilledQuantity()).thenReturn(filled);
    return r;
  }

  @Test
  void partialRecallIsDetectedOnceAndUsesUnfulfilledQuantity() throws Exception {
    var r = recall(now.minusSeconds(1), 80, 30);
    when(recalls.findByLoanId(7L)).thenReturn(List.of(r));
    service.evaluate(1, 7);
    service.evaluate(1, 7);
    assertEquals(1, stored.size());
    assertEquals(1, reports.size());
    assertEquals(50, reports.get(0).path("data").path("quantity").asLong());
    assertEquals("OPEN", stored.get(0).getStatus());
    when(r.getFulfilledQuantity()).thenReturn(60L);
    service.evaluate(1, 7);
    assertEquals(20, stored.get(0).getQuantity());
    assertEquals(2, stored.get(0).getVersion());
    when(r.getStatus()).thenReturn("FULFILLED");
    service.evaluate(1, 7);
    service.evaluate(1, 7);
    assertEquals("RESOLVED", stored.get(0).getStatus());
    assertEquals(3, reports.size());
  }

  @Test
  void maturityRequiresAnElapsedDeadlineAndSettledOutstandingSecurities() throws Exception {
    loan.setMaturityAt(now);
    service.evaluate(1, 7);
    assertTrue(stored.isEmpty());
    loan.setMaturityAt(now.minusSeconds(1));
    service.evaluate(1, 7);
    assertEquals("MATURITY", stored.get(0).getObligationType());
    loan.setOutstandingQuantity(0L);
    service.evaluate(1, 7);
    assertEquals("RESOLVED", stored.get(0).getStatus());
  }

  @Test
  void futureDeadlinesAndUnsettledLoansDoNotDefault() throws Exception {
    var r = recall(now.plusSeconds(10), 20, 0);
    when(recalls.findByLoanId(7L)).thenReturn(List.of(r));
    service.evaluate(1, 7);
    loan.setMaturityAt(now.minusSeconds(1));
    loan.setSettledAt(null);
    service.evaluate(1, 7);
    assertTrue(stored.isEmpty());
  }

  @Test
  void marginShortfallUsesOnlyUnfulfilledAmountAndDoesNotCreateSecurityQuantity() throws Exception {
    var m = new MarginCall();
    m.setOrganizationId(1L);
    m.setOwnerReferenceId(9L);
    m.setStatus("PARTIALLY_FULFILLED");
    m.setAmount(new BigDecimal("1000"));
    m.setFulfilledAmount(new BigDecimal("400"));
    m.setDueAt(now.minusSeconds(1));
    when(margins.findByLoanId(7L)).thenReturn(List.of(m));
    service.evaluate(1, 7);
    assertEquals("MARGIN_CALL", stored.get(0).getObligationType());
    assertEquals(0, stored.get(0).getQuantity());
    assertEquals(
      0,
      new BigDecimal("600").compareTo(stored.get(0).getExposure())
    );
    m.setStatus("FULFILLED");
    service.evaluate(1, 7);
    assertEquals("RESOLVED", stored.get(0).getStatus());
  }

  @Test
  void closingLoanResolvesOpenCasesAndAnotherTenantCannotEvaluateIt() throws Exception {
    loan.setMaturityAt(now.minusSeconds(1));
    service.evaluate(1, 7);
    loan.setStatus("CLOSED");
    service.evaluate(1, 7);
    assertEquals("RESOLVED", stored.get(0).getStatus());
    service.evaluate(2, 7);
    verify(loans, never()).findByIdAndOrganizationId(7L, 2L);
  }
}
