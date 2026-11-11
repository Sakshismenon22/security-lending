package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.MarginCall;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarginCallRepository extends JpaRepository<MarginCall, Long> {
  List<MarginCall> findByLoanId(Long loanId);

  Optional<MarginCall> findFirstByOrganizationIdAndOwnerReferenceIdOrderByCreatedAtDesc(
      Long organizationId, Long ownerReferenceId);
}
