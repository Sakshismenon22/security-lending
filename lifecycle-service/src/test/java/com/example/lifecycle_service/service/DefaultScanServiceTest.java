package com.example.lifecycle_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.lifecycle_service.controller.DefaultScanController;
import com.example.lifecycle_service.model.*;
import com.example.lifecycle_service.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DefaultScanServiceTest {

  OrganizationRepository orgs = mock(OrganizationRepository.class);
  LoanRepository loans = mock(LoanRepository.class);
  DefaultCaseRecordRepository cases = mock(DefaultCaseRecordRepository.class);
  DefaultLifecycleService lifecycle = mock(DefaultLifecycleService.class);
  DefaultScanService scans = new DefaultScanService(
    orgs,
    loans,
    cases,
    lifecycle,
    Clock.fixed(Instant.parse("2026-11-18T12:00:00Z"), ZoneOffset.UTC)
  );

  @Test
  void scanIsTenantScopedDeduplicatedAndContinuesAfterOneLoanFails() throws Exception {
    when(orgs.existsById(1L)).thenReturn(true);
    var first = new Loan();
    first.setId(7L);
    var second = new Loan();
    second.setId(9L);
    when(
      loans.findByOrganizationIdAndStatusIn(1L, Set.of("ACTIVE", "DEFAULTED"))
    ).thenReturn(List.of(first, second));
    var duplicate = new DefaultCaseRecord();
    duplicate.setLoanId(7L);
    var closed = new DefaultCaseRecord();
    closed.setLoanId(11L);
    when(cases.findByOrganizationIdAndStatus(1L, "OPEN")).thenReturn(
      List.of(duplicate, closed)
    );
    when(cases.countByOrganizationIdAndStatus(1L, "OPEN")).thenReturn(2L);
    doThrow(new IllegalStateException("Transient error"))
      .when(lifecycle)
      .evaluate(1, 9);
    var result = scans.run(1);
    assertEquals(3, result.get("loansChecked"));
    assertEquals(2, result.get("loansSucceeded"));
    assertEquals(1, result.get("loansFailed"));
    assertEquals(List.of(9L), result.get("failedLoanIds"));
    assertEquals(2L, result.get("openCases"));
    verify(lifecycle).evaluate(1, 7);
    verify(lifecycle).evaluate(1, 9);
    verify(lifecycle).evaluate(1, 11);
    verifyNoMoreInteractions(lifecycle);
  }

  @Test
  void unknownOrganizationCannotStartAScan() {
    assertEquals(
      404,
      assertThrows(ResponseStatusException.class, () -> scans.run(5))
        .getStatusCode()
        .value()
    );
    verifyNoInteractions(lifecycle, loans, cases);
  }

  @Test
  void privateControlEndpointRequiresTheSharedKey() {
    var scan = mock(DefaultScanService.class);
    var controller = new DefaultScanController(scan, "test-key");
    assertEquals(
      401,
      assertThrows(ResponseStatusException.class, () -> controller.run(1, null))
        .getStatusCode()
        .value()
    );
    assertThrows(ResponseStatusException.class, () ->
      controller.run(1, "wrong-key")
    );
    verifyNoInteractions(scan);
    when(scan.run(1)).thenReturn(Map.of("organizationId", 1));
    assertEquals(true, controller.run(1, "test-key").get("success"));
    assertEquals(
      503,
      assertThrows(ResponseStatusException.class, () ->
        new DefaultScanController(scan, "").run(1, "test-key")
      )
        .getStatusCode()
        .value()
    );
  }
}
