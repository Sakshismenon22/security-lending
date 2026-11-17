package com.example.fix_adapter_service.repository;

import com.example.fix_adapter_service.model.*;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface KafkaPublicationRepository
  extends JpaRepository<KafkaPublication, Long>
{
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from KafkaPublication p where p.id=:id")
  Optional<KafkaPublication> lockById(long id);

  @Query(
    "select p.id from KafkaPublication p where p.state=com.example.fix_adapter_service.model.PublicationState.PENDING and p.nextAttemptAt<=:now order by p.id"
  )
  List<Long> due(Instant now, Pageable pageable);

  @Query(
    "select distinct p.partitionKey from KafkaPublication p where p.state not in (com.example.fix_adapter_service.model.PublicationState.PUBLISHED,com.example.fix_adapter_service.model.PublicationState.CLOSED)"
  )
  List<String> pendingPartitions();

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
    "select p from KafkaPublication p where p.partitionKey=:partitionKey and p.state not in (com.example.fix_adapter_service.model.PublicationState.PUBLISHED,com.example.fix_adapter_service.model.PublicationState.CLOSED) order by p.id"
  )
  List<KafkaPublication> lockHead(String partitionKey, Pageable pageable);

  org.springframework.data.domain.Page<KafkaPublication> findByStateIn(
    java.util.Collection<PublicationState> states,
    Pageable pageable
  );
  long countByState(PublicationState state);
}
