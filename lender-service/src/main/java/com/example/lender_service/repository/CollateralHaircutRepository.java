package com.example.lender_service.repository;

import com.example.lender_service.model.CollateralHaircut;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface CollateralHaircutRepository extends JpaRepository<CollateralHaircut, Long> {
  Page<CollateralHaircut> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<CollateralHaircut> findByIdAndOrganizationId(Long id, Long organizationId);

  List<CollateralHaircut> findByMslaId(Long mslaId);
}
