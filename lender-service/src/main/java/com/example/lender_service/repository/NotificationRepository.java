package com.example.lender_service.repository;

import com.example.lender_service.model.Notification;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  Page<Notification> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<Notification> findByIdAndOrganizationId(Long id, Long organizationId);
}
