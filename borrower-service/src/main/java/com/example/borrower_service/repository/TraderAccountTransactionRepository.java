package com.example.borrower_service.repository;

import com.example.borrower_service.model.TraderAccountTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TraderAccountTransactionRepository extends JpaRepository<TraderAccountTransaction,Long> {
    List<TraderAccountTransaction> findByOrganizationId(Long organizationId);
    Page<TraderAccountTransaction> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<TraderAccountTransaction> findByIdAndOrganizationId(Long id,Long organizationId);
}
