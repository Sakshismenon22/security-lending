package com.example.lifecycle_service.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

enum RebateSchedule {
  DAILY,
  WEEKLY,
  MONTHLY,
  AT_CLOSE;

  LocalDate periodEnd(LocalDate accrualDate, LocalDate closedOn) {
    LocalDate scheduled = switch (this) {
      case DAILY -> accrualDate;
      case WEEKLY -> accrualDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
      case MONTHLY -> accrualDate.with(TemporalAdjusters.lastDayOfMonth());
      case AT_CLOSE -> closedOn;
    };
    return closedOn != null && scheduled.isAfter(closedOn) ? closedOn : scheduled;
  }
}
