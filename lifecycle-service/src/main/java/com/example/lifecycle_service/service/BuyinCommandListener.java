package com.example.lifecycle_service.service;

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
  matchIfMissing = true
)
public class BuyinCommandListener {

  private final ObjectMapper json;
  private final BuyinLifecycleService buyins;

  @KafkaListener(
    topics = "${lifecycle.lender-commands-topic:lender.lifecycle.commands.v1}",
    groupId = "lifecycle-buyins"
  )
  public void receive(String body) throws Exception {
    var e = json.readTree(body);
    if (
      !java.util.Set.of(
        "BUY_IN_REQUESTED",
        "BUY_IN_EXECUTION_REQUESTED"
      ).contains(e.path("type").asText())
    ) return;
    long org = e.path("organizationId").asLong(),
      id = e.path("referenceId").asLong();
    if (org <= 0 || id <= 0) throw new IllegalArgumentException(
      "Invalid buy-in command IDs"
    );
    buyins.process(org, id);
  }
}
