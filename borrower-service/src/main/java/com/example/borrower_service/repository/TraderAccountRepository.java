package com.example.borrower_service.repository;

import com.example.borrower_service.model.TraderAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TraderAccountRepository extends JpaRepository<TraderAccount,Long> {
    List<TraderAccount> findByOrganizationId(Long organizationId);
    Page<TraderAccount> findByOrganizationId(Long organizationId,Pageable pageable);
}
