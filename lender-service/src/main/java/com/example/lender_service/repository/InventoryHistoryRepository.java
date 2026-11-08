package com.example.lender_service.repository;

import com.example.lender_service.model.InventoryHistory;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface InventoryHistoryRepository extends JpaRepository<InventoryHistory, Long> {
  Page<InventoryHistory> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<InventoryHistory> findByIdAndOrganizationId(Long id, Long organizationId);
}
