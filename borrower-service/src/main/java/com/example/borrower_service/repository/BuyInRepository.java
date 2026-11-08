package com.example.borrower_service.repository;

import com.example.borrower_service.model.BuyIn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface BuyInRepository extends JpaRepository<BuyIn,Long> {
    List<BuyIn> findByOrganizationId(Long organizationId);
    Page<BuyIn> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<BuyIn> findByIdAndOrganizationId(Long id,Long organizationId);
}
