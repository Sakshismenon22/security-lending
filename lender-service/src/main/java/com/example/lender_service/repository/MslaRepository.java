package com.example.lender_service.repository;

import com.example.lender_service.model.Msla;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface MslaRepository extends JpaRepository<Msla, Long> {
  Page<Msla> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<Msla> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<Msla> findByConnectionIdAndBorrowerMslaId(Long connectionId, Long borrowerMslaId);
}
