package com.example.lender_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lender_service.dto.SettlementRequest;
import com.example.lender_service.model.BorrowerConnection;
import com.example.lender_service.model.Loan;
import com.example.lender_service.model.MarginCall;
import com.example.lender_service.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarginCallServiceTest {
  @Mock private MarginCallRepository calls;
  @Mock private LoanRepository loans;
  @Mock private BorrowerConnectionRepository connections;
  @Mock private EventService events;
  @Mock private TenantService tenants;

  @InjectMocks private MarginCallService service;

  @Test
  void confirmEmitsBorrowerMarginConfirmation() {
    long org = 1L;
    long loanId = 7L;
    long callId = 42L;
    long borrowerLoanId = 99L;
    long connectionId = 5L;

    var call = new MarginCall();
    call.setId(callId);
    call.setOrganizationId(org);
    call.setConnectionId(connectionId);
    call.setLoanId(loanId);
    call.setOwnerReferenceId(123L);
    call.setStatus("OPEN");
    call.setAmount(new BigDecimal("250.00"));
    call.setFulfilledAmount(BigDecimal.ZERO);
    call.setDueAt(Instant.parse("2025-05-01T15:00:00Z"));
    call.setReason("Coverage below policy");

    var loan = new Loan();
    loan.setId(loanId);
    loan.setOrganizationId(org);
    loan.setConnectionId(connectionId);
    loan.setBorrowerLoanId(borrowerLoanId);

    var connection = new BorrowerConnection();
    connection.setId(connectionId);
    connection.setOrganizationId(org);
    connection.setBorrowerOrganizationId(11L);
    connection.setExternalId("BORROWER_DEMO");

    when(calls.findByIdAndOrganizationId(callId, org)).thenReturn(Optional.of(call));
    when(loans.findByIdAndOrganizationId(loanId, org)).thenReturn(Optional.of(loan));
    when(connections.findById(connectionId)).thenReturn(Optional.of(connection));

    Map<String, Object> result = service.confirm(org, callId, new SettlementRequest("REF-1"));

    assertEquals("CONFIRMED", result.get("status"));
    assertEquals(callId, result.get("marginCallId"));
    verify(events)
        .confirm(
            eq(connection),
            eq("MARGIN_CALL_UPDATED"),
            eq("MARGIN_CALL_UPDATED"),
            eq(call.getOwnerReferenceId()),
            anyMap());
  }
}
