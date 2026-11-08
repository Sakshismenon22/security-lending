package com.example.borrower_service.repository;

import com.example.borrower_service.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification,Long> {
    List<Notification> findByOrganizationId(Long organizationId);
    Page<Notification> findByOrganizationId(Long organizationId,Pageable pageable);
    Optional<Notification> findByIdAndOrganizationId(Long id,Long organizationId);
}
