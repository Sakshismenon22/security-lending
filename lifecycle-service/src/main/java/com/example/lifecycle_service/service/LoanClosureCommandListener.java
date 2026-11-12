package com.example.lifecycle_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Runs final fee and rebate processing when the lender settles the last returned share. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "lifecycle.kafka-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class LoanClosureCommandListener {
  private final ObjectMapper json;
  private final LendingFeeService fees;
  private final RebateLifecycleService rebates;

  @KafkaListener(
      topics = "${lifecycle.lender-commands-topic:lender.lifecycle.commands.v1}",
      groupId = "lifecycle-loan-closure")
  public void receive(String body) throws JsonProcessingException {
    var command = json.readTree(body);
    if (!"LOAN_CLOSED".equals(command.path("type").asText())) return;
    long loanId = command.path("data").path("loanId").asLong();
    if (loanId <= 0) throw new IllegalArgumentException("Invalid closed-loan command");
    fees.process(loanId);
    rebates.process(loanId);
  }
}
