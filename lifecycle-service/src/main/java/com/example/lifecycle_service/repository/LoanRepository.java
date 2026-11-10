package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.Loan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepository extends JpaRepository<Loan, Long> {
  Optional<Loan> findByIdAndOrganizationId(Long id, Long organizationId);

  List<Loan> findAllByStatus(String status);
}
