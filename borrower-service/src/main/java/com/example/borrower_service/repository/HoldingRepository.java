package com.example.borrower_service.repository;

import com.example.borrower_service.model.Holding;
import com.example.borrower_service.model.HoldingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding,HoldingId> {
    List<Holding> findByOrganizationId(Long organizationId);
    Page<Holding> findByOrganizationId(Long organizationId,Pageable pageable);
}
