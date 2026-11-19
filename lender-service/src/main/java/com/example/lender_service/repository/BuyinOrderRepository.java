package com.example.lender_service.repository;

import com.example.lender_service.model.BuyinOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BuyinOrderRepository extends JpaRepository<BuyinOrder, Long> {
  Optional<BuyinOrder> findByIdAndOrganizationId(Long id, Long org);
  Optional<BuyinOrder> findByOrganizationIdAndClientRequestId(
    Long org,
    String clientId
  );
  Optional<BuyinOrder> findByOrganizationIdAndExecutionRequestId(
    Long org,
    Long requestId
  );
  List<BuyinOrder> findByOrganizationId(Long org);
  List<BuyinOrder> findByStatusIn(Collection<String> statuses);
  List<BuyinOrder> findByOrganizationIdAndDefaultId(Long org, Long defaultId);
}
