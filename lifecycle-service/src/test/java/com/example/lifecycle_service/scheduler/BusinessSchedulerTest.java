package com.example.lifecycle_service.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.lifecycle_service.model.Loan;
import com.example.lifecycle_service.repository.LoanRepository;
import com.example.lifecycle_service.service.*;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

class BusinessSchedulerTest {
  @Test
  void dailyFeesIncludeLoansProcessedThroughYesterdayAndContinueAfterFailure() {
    var loans = mock(LoanRepository.class);
    var fees = mock(LendingFeeService.class);
    var today = LocalDate.of(2026, 11, 16);
    when(fees.lastCompletedDay()).thenReturn(today.minusDays(1));
    when(loans.findFeeLoanIds(today)).thenReturn(List.of(1L, 2L));
    when(fees.process(1L)).thenThrow(new IllegalStateException("Missing quote"));
    new LendingFeeScheduler(loans, fees).processDueFees();
    verify(fees).process(2L);
  }

  @Test
  void rebatesContinueAfterOneLoanFails() {
    var loans = mock(LoanRepository.class);
    var rebates = mock(RebateLifecycleService.class);
    when(loans.findRebateLoanIds()).thenReturn(List.of(1L, 2L));
    when(rebates.process(1L)).thenThrow(new IllegalStateException("Invalid agreement"));
    new RebateScheduler(loans, rebates).processDueRebates();
    verify(rebates).process(2L);
  }

  @Test
  void valuationsSkipZeroOutstandingAndContinueAfterMissingPrice() {
    var loans = mock(LoanRepository.class);
    var valuations = mock(LoanValuationService.class);
    var first = mock(Loan.class);
    var empty = mock(Loan.class);
    var next = mock(Loan.class);
    when(first.getId()).thenReturn(1L);
    when(first.getOutstandingQuantity()).thenReturn(10L);
    when(empty.getOutstandingQuantity()).thenReturn(0L);
    when(next.getId()).thenReturn(3L);
    when(next.getOutstandingQuantity()).thenReturn(20L);
    when(loans.findAllByStatus("ACTIVE")).thenReturn(List.of(first, empty, next));
    when(valuations.runValuationForLoan(1L)).thenThrow(new IllegalStateException("Missing quote"));
    new LoanValuationScheduler(loans, valuations).valueActiveLoans();
    verify(valuations).runValuationForLoan(3L);
    verify(valuations, times(2)).runValuationForLoan(anyLong());
  }

  @Test
  void schedulesUseIndiaTimeAndExpectedNextExecution() throws Exception {
    var now = ZonedDateTime.parse("2026-11-16T15:14:00+05:30[Asia/Kolkata]");
    for (var entry : List.of(
        new Object[]{LendingFeeScheduler.class, "processDueFees", "0 15 15 * * *"},
        new Object[]{RebateScheduler.class, "processDueRebates", "0 15 15 * * *"},
        new Object[]{LoanValuationScheduler.class, "valueActiveLoans", "0 0 * * * *"})) {
      var annotation = ((Class<?>)entry[0]).getMethod((String)entry[1]).getAnnotation(Scheduled.class);
      assertEquals("Asia/Kolkata", annotation.zone());
      String cron = (String)entry[2];
      assertTrue(annotation.cron().endsWith(":" + cron + "}"));
      assertEquals(cron.equals("0 0 * * * *") ? now.withHour(16).withMinute(0) : now.withMinute(15),
          CronExpression.parse(cron).next(now));
    }
  }
}
