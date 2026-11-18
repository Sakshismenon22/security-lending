package com.example.lender_service.repository;

import com.example.lender_service.model.BuyinLoanCloseout;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyinLoanCloseoutRepository
  extends JpaRepository<BuyinLoanCloseout, Long>
{
  List<BuyinLoanCloseout> findTop100ByBorrowerSummaryQueuedFalseOrderByIdAsc();
  Optional<BuyinLoanCloseout> findByOrganizationIdAndLoanId(
    Long org,
    Long loan
  );
  Optional<BuyinLoanCloseout> findByOrganizationIdAndReference(
    Long org,
    String reference
  );
}
