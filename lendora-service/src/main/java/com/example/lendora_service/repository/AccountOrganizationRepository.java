package com.example.lendora_service.repository;

import com.example.lendora_service.model.AccountOrganization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountOrganizationRepository
  extends JpaRepository<AccountOrganization, Long>
{
  java.util.List<AccountOrganization> findByTypeAndPlatformOrganizationIdIsNotNullOrderByNameAsc(
    String type
  );

  boolean existsByTypeAndRegistrationNumber(
    String type,
    String registrationNumber
  );
}
