package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.RecallRecord;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RecallRecordRepository extends Repository<RecallRecord, Long> {
  List<RecallRecord> findByStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
      Collection<String> statuses, Instant dueAt);

  Optional<RecallRecord> findById(Long id);
}
