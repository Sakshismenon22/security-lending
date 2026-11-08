package com.example.borrower_service.repository;

import com.example.borrower_service.model.RebatePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface RebatePaymentRepository extends JpaRepository<RebatePayment,Long> {
    List<RebatePayment> findByOrganizationId(Long organizationId);
    Page<RebatePayment> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<RebatePayment> findByIdAndOrganizationId(Long id,Long organizationId);
}
