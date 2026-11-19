package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository
  extends JpaRepository<Organization, Long> {}
