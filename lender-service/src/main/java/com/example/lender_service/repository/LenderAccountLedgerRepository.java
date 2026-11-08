package com.example.lender_service.repository;

import com.example.lender_service.model.LenderAccountLedger;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LenderAccountLedgerRepository extends JpaRepository<LenderAccountLedger, Long> {
  Page<LenderAccountLedger> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<LenderAccountLedger> findByIdAndOrganizationId(Long id, Long organizationId);
}
