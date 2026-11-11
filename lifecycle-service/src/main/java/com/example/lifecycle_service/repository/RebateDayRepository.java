package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.RebateDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RebateDayRepository extends JpaRepository<RebateDay, Long> {
  Optional<RebateDay> findByLoanIdAndAccrualDate(long loanId, LocalDate accrualDate);

  List<RebateDay> findByLoanIdAndSettlementReferenceIdIsNullOrderByAccrualDateAsc(long loanId);
}
