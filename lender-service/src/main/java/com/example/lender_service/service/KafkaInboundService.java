package com.example.lender_service.service;

import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "lender.kafka-enabled", havingValue = "true")
public class KafkaInboundService {
  private final BorrowerCommandService commands;
  private final LifecycleEventService lifecycle;

  @KafkaListener(topics = "${lender.commands-topic}", groupId = "lender-service")
  public void borrower(ConsumerRecord<String, String> record) {
    var header = record.headers().lastHeader("x-counterparty-id");
    if (header == null)
      throw new IllegalArgumentException("Command lacks adapter counterparty metadata");
    commands.receive(record.value(), new String(header.value(), StandardCharsets.UTF_8));
  }

  @KafkaListener(topics = "${lender.lifecycle-events-topic}", groupId = "lender-service-lifecycle")
  public void lifecycle(String body) {
    lifecycle.receive(body);
  }
}
