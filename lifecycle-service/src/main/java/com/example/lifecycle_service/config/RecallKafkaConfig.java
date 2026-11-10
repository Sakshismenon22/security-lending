package com.example.lifecycle_service.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(
    name = "lifecycle.kafka-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RecallKafkaConfig {
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumer, KafkaTemplate<String, String> producer) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumer);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    var recoverer =
        new DeadLetterPublishingRecoverer(
            producer,
            (record, error) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3));
    handler.addNotRetryableExceptions(IllegalArgumentException.class);
    factory.setCommonErrorHandler(handler);
    return factory;
  }
}
