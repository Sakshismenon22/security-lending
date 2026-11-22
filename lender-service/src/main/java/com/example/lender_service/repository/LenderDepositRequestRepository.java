package com.example.lender_service.repository;

import com.example.lender_service.model.LenderDepositRequest;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LenderDepositRequestRepository extends JpaRepository<LenderDepositRequest, Long> {
  Page<LenderDepositRequest> findByOrganizationId(long organizationId, Pageable page);
  Optional<LenderDepositRequest> findByOrganizationIdAndId(long organizationId, long id);
  Optional<LenderDepositRequest> findByOrganizationIdAndClientRequestId(long organizationId, String clientRequestId);
}
