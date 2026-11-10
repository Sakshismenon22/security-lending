package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.LoanValuation;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanValuationRepository extends JpaRepository<LoanValuation, Long> {
  Optional<LoanValuation> findFirstByLoanIdOrderByValuedAtDescIdDesc(Long loanId);
}
