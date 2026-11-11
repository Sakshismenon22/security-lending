package com.example.borrower_service.repository;

import com.example.borrower_service.model.LendingFee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LendingFeeRepository extends JpaRepository<LendingFee,Long> {
    List<LendingFee> findByOrganizationId(Long organizationId);
    Page<LendingFee> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<LendingFee> findByIdAndOrganizationId(Long id,Long organizationId);
    Page<LendingFee> findByOrganizationIdAndLoanId(Long organizationId, Long loanId, Pageable pageable);
}
