package com.example.borrower_service.repository;

import com.example.borrower_service.model.LoanReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface LoanReturnRepository extends JpaRepository<LoanReturn,Long> {
    List<LoanReturn> findByOrganizationId(Long organizationId);
    Page<LoanReturn> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<LoanReturn> findByIdAndOrganizationId(Long id,Long organizationId);
}
