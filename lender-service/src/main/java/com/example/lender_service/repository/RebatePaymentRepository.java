package com.example.lender_service.repository;

import com.example.lender_service.model.RebatePayment;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface RebatePaymentRepository extends JpaRepository<RebatePayment, Long> {
  Page<RebatePayment> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<RebatePayment> findByIdAndOrganizationId(Long id, Long organizationId);

  Page<RebatePayment> findByOrganizationIdAndLoanId(Long organizationId, Long loanId, Pageable pageable);
}
