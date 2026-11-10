package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.FeeLoanReturn;
import java.time.Instant;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface FeeLoanReturnRepository extends Repository<FeeLoanReturn, Long> {
  List<FeeLoanReturn> findByLoanIdAndStatusAndSettledAtBeforeOrderBySettledAtAscIdAsc(
      long loanId, String status, Instant cutoff);
}
