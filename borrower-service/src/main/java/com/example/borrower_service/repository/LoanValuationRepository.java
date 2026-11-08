package com.example.borrower_service.repository;

import com.example.borrower_service.model.LoanValuation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LoanValuationRepository extends JpaRepository<LoanValuation,Long> {
    List<LoanValuation> findByOrganizationId(Long organizationId);
    Page<LoanValuation> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<LoanValuation> findByIdAndOrganizationId(Long id,Long organizationId);
}
