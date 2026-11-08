package com.example.borrower_service.repository;

import com.example.borrower_service.model.CreditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface CreditHistoryRepository extends JpaRepository<CreditHistory,Long> {
    List<CreditHistory> findByOrganizationId(Long organizationId);
    Page<CreditHistory> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<CreditHistory> findByIdAndOrganizationId(Long id,Long organizationId);
}
