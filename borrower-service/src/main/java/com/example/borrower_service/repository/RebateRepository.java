package com.example.borrower_service.repository;

import com.example.borrower_service.model.Rebate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface RebateRepository extends JpaRepository<Rebate,Long> {
    List<Rebate> findByOrganizationId(Long organizationId);
    Page<Rebate> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Rebate> findByIdAndOrganizationId(Long id,Long organizationId);

  Page<Rebate> findByOrganizationIdAndLoanId(Long organizationId, Long loanId, Pageable pageable);
}
