package com.example.lifecycle_service.repository;

import com.example.lifecycle_service.model.LenderOutboxEvent;
import org.springframework.data.repository.Repository;

public interface LenderOutboxEventRepository extends Repository<LenderOutboxEvent, Long> {
  boolean existsByEventId(String eventId);

  LenderOutboxEvent save(LenderOutboxEvent event);
}
