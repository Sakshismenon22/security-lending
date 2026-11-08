package com.example.borrower_service.repository;

import com.example.borrower_service.model.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LoanRepository extends JpaRepository<Loan,Long> {
    List<Loan> findByOrganizationId(Long organizationId);
    Page<Loan> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Loan> findByIdAndOrganizationId(Long id,Long organizationId);
}
