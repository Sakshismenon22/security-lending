package com.example.borrower_service.repository;

import com.example.borrower_service.model.CollateralHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CollateralHistoryRepository extends JpaRepository<CollateralHistory,Long> {
    List<CollateralHistory> findByOrganizationId(Long organizationId);
    Page<CollateralHistory> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<CollateralHistory> findByIdAndOrganizationId(Long id,Long organizationId);
}
