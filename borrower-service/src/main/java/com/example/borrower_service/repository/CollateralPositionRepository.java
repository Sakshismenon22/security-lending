package com.example.borrower_service.repository;

import com.example.borrower_service.model.CollateralPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CollateralPositionRepository extends JpaRepository<CollateralPosition,Long> {
    List<CollateralPosition> findByOrganizationId(Long organizationId);
    Page<CollateralPosition> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<CollateralPosition> findByIdAndOrganizationId(Long id,Long organizationId);
}
