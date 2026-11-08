package com.example.lender_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(name = "lender.kafka-enabled", havingValue = "true")
public class KafkaConfig {
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumer, KafkaTemplate<String, String> kafka) {
    var f = new ConcurrentKafkaListenerContainerFactory<String, String>();
    f.setConsumerFactory(consumer);
    var recoverer =
        new DeadLetterPublishingRecoverer(
            kafka,
            (record, error) ->
                new org.apache.kafka.common.TopicPartition(
                    record.topic() + ".DLT", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    var errors = new DefaultErrorHandler(recoverer, new FixedBackOff(1000, 3));
    errors.addNotRetryableExceptions(IllegalArgumentException.class);
    f.setCommonErrorHandler(errors);
    f.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
    return f;
  }
}
