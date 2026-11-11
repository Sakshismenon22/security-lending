package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.RecallRequestRecord;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RecallRequestRecordRepository extends Repository<RecallRequestRecord, Long> {
  Optional<RecallRequestRecord> findByIdAndOrganizationId(Long id, Long organizationId);
}
