package com.example.borrower_service.repository;

import com.example.borrower_service.model.LoanDefault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LoanDefaultRepository extends JpaRepository<LoanDefault,Long> {
    List<LoanDefault> findByOrganizationId(Long organizationId);
    Page<LoanDefault> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<LoanDefault> findByIdAndOrganizationId(Long id,Long organizationId);
}
