package com.example.lender_service.repository;

import com.example.lender_service.model.Organization;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
  Optional<Organization> findByExternalId(String externalId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Organization o where o.id=:id")
  Optional<Organization> lockById(long id);
}
