package com.example.borrower_service.repository;

import com.example.borrower_service.model.LocateRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LocateRequestRepository extends JpaRepository<LocateRequest,Long> {
    List<LocateRequest> findByOrganizationId(Long organizationId);
    Page<LocateRequest> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<LocateRequest> findByIdAndOrganizationId(Long id,Long organizationId);
}
