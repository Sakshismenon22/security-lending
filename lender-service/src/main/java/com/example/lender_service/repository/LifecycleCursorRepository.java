package com.example.lender_service.repository;

import com.example.lender_service.model.LifecycleCursor;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LifecycleCursorRepository extends JpaRepository<LifecycleCursor, Long> {
  Optional<LifecycleCursor> findByOrganizationIdAndResourceAndReferenceId(
      Long organizationId, String resource, Long referenceId);
}
