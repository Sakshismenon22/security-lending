package com.example.lifecycle_service.scheduler;

import com.example.lifecycle_service.repository.RecallRecordRepository;
import com.example.lifecycle_service.service.RecallLifecycleService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "lifecycle.kafka-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecallDeadlineScheduler {
  private final RecallRecordRepository recalls;
  private final RecallLifecycleService lifecycle;

  @Scheduled(fixedDelayString = "${lifecycle.recall-deadline-poll-ms:5000}")
  public void checkDeadlines() {
    for (var recall :
        recalls.findByStatusInAndDueAtLessThanEqualOrderByDueAtAsc(
            RecallLifecycleService.DEADLINE_STATUSES, Instant.now())) {
      try {
        lifecycle.reportDeadline(recall.getOrganizationId(), recall.getId());
      } catch (Exception exception) {
        log.error("Could not report deadline for recall {}", recall.getId(), exception);
      }
    }
  }
}
