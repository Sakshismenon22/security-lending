package com.example.borrower_service.repository;

import com.example.borrower_service.model.FeeAccrualView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface FeeAccrualViewRepository extends JpaRepository<FeeAccrualView,Long> {
    List<FeeAccrualView> findByOrganizationId(Long organizationId);
    Page<FeeAccrualView> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<FeeAccrualView> findByIdAndOrganizationId(Long id,Long organizationId);
}
