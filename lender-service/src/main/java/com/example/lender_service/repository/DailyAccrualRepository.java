package com.example.lender_service.repository;

import com.example.lender_service.model.DailyAccrual;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface DailyAccrualRepository extends JpaRepository<DailyAccrual, Long> {
  Page<DailyAccrual> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<DailyAccrual> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<DailyAccrual> findByOrganizationIdAndOwnerReferenceId(
      Long organizationId, Long ownerReferenceId);
}
