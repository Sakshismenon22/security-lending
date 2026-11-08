package com.example.lender_service.repository;

import com.example.lender_service.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  Page<OutboxEvent> findByOrganizationId(Long organizationId, Pageable pageable);

  Optional<OutboxEvent> findByIdAndOrganizationId(Long id, Long organizationId);

  @Query(
      "select distinct e.partitionKey from OutboxEvent e where e.wsSent=false or"
          + " (:kafkaEnabled=true and e.published=false)")
  List<String> pendingPartitions(boolean kafkaEnabled);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select e from OutboxEvent e where e.partitionKey=:key and (e.wsSent=false or"
          + " (:kafkaEnabled=true and e.published=false)) order by e.id")
  List<OutboxEvent> lockHead(String key, boolean kafkaEnabled, Pageable pageable);
}
