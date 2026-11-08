package com.example.borrower_service.repository;

import com.example.borrower_service.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent,Long> {
    List<AuditEvent> findByOrganizationId(Long organizationId);
    Page<AuditEvent> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<AuditEvent> findByIdAndOrganizationId(Long id,Long organizationId);
}
