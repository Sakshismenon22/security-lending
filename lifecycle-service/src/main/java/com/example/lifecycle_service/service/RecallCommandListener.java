package com.example.lifecycle_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "lifecycle.kafka-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecallCommandListener {
  private final ObjectMapper json;
  private final RecallLifecycleService recalls;

  @KafkaListener(
      topics = "${lifecycle.lender-commands-topic:lender.lifecycle.commands.v1}",
      groupId = "lifecycle-recalls")
  public void receive(String body) throws JsonProcessingException {
    var command = json.readTree(body);
    if (!"RECALL_REQUESTED".equals(command.path("type").asText())) return;
    long org = command.path("organizationId").asLong();
    long requestId = command.path("referenceId").asLong();
    if (org <= 0 || requestId <= 0)
      throw new IllegalArgumentException("Invalid recall command IDs");
    recalls.issue(org, requestId);
  }
}
