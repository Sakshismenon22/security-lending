package com.example.borrower_service.repository;

import com.example.borrower_service.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent,Long> {
    List<OutboxEvent> findByOrganizationId(Long organizationId);
    Page<OutboxEvent> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<OutboxEvent> findByIdAndOrganizationId(Long id,Long organizationId);
}
