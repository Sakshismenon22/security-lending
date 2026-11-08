package com.example.borrower_service.repository;

import com.example.borrower_service.model.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest,Long> {
    List<PaymentRequest> findByOrganizationId(Long organizationId);
    Page<PaymentRequest> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<PaymentRequest> findByIdAndOrganizationId(Long id,Long organizationId);
}
