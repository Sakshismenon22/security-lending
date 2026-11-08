package com.example.lender_service.repository;

import com.example.lender_service.model.InboxEvent;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface InboxEventRepository extends JpaRepository<InboxEvent, String> {
  Page<InboxEvent> findByOrganizationId(Long organizationId, Pageable pageable);
}
