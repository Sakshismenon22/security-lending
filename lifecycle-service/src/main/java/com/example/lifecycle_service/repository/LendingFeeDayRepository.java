package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.LendingFeeDay;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LendingFeeDayRepository extends JpaRepository<LendingFeeDay, Long> {
  Optional<LendingFeeDay> findByLoanIdAndAccrualDate(long loanId, LocalDate date);

  List<LendingFeeDay> findByLoanIdAndBilledFalseOrderByAccrualDateAsc(long loanId);
}
