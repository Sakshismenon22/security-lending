package com.example.borrower_service.repository;

import com.example.borrower_service.model.TradeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TradeHistoryRepository extends JpaRepository<TradeHistory,Long> {
    List<TradeHistory> findByOrganizationId(Long organizationId);
    Page<TradeHistory> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<TradeHistory> findByIdAndOrganizationId(Long id,Long organizationId);
}
