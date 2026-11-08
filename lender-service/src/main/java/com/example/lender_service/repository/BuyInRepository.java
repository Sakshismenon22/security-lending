package com.example.lender_service.repository;

import com.example.lender_service.model.BuyIn;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface BuyInRepository extends JpaRepository<BuyIn, Long> {
  Page<BuyIn> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<BuyIn> findByIdAndOrganizationId(Long id, Long organizationId);

  Optional<BuyIn> findByOrganizationIdAndOwnerReferenceId(
      Long organizationId, Long ownerReferenceId);
}
