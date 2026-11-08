package com.example.lender_service.repository;

import com.example.lender_service.model.LoanDefault;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface LoanDefaultRepository extends JpaRepository<LoanDefault, Long> {
  Page<LoanDefault> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<LoanDefault> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<LoanDefault> findByOrganizationIdAndOwnerReferenceId(
      Long organizationId, Long ownerReferenceId);
}
