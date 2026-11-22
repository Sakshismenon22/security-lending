package com.example.lender_service.repository;

import com.example.lender_service.model.BuyinOrder;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BuyinOrderRepository extends JpaRepository<BuyinOrder, Long> {
  @Query("""
      select coalesce(sum(o.lenderFundsReserved), 0) from BuyinOrder o, Loan l
      where o.organizationId = :org and o.loanId = l.id
        and o.status in ('EXECUTION_PENDING', 'FILLED') and l.outstandingQuantity > 0
        and (:exceptLoan is null or o.loanId <> :exceptLoan)
      """)
  BigDecimal reservedFunding(@Param("org") long org, @Param("exceptLoan") Long exceptLoan);

  Optional<BuyinOrder> findByIdAndOrganizationId(Long id, Long org);
  Optional<BuyinOrder> findByOrganizationIdAndClientRequestId(
    Long org,
    String clientId
  );
  Optional<BuyinOrder> findByOrganizationIdAndExecutionRequestId(
    Long org,
    Long requestId
  );
  List<BuyinOrder> findByOrganizationId(Long org);
  List<BuyinOrder> findByStatusIn(Collection<String> statuses);
  List<BuyinOrder> findByOrganizationIdAndDefaultId(Long org, Long defaultId);
}
