package com.example.borrower_service.repository;

import com.example.borrower_service.model.TradeOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface TradeOrderRepository extends JpaRepository<TradeOrder,Long> {
    List<TradeOrder> findByOrganizationId(Long organizationId);
    Page<TradeOrder> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<TradeOrder> findByIdAndOrganizationId(Long id,Long organizationId);
}
