package com.example.fix_adapter_service.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(name="adapter.kafka-enabled",havingValue="true")
public class KafkaConfig {
    @Bean public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(ConsumerFactory<String,String> consumers,KafkaTemplate<String,String> producer) {
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();factory.setConsumerFactory(consumers);
        var recoverer=new DeadLetterPublishingRecoverer(producer,(record,error)->new org.apache.kafka.common.TopicPartition(record.topic()+".DLT",record.partition()));recoverer.setFailIfSendResultIsError(true);
        var errors=new DefaultErrorHandler(recoverer,new FixedBackOff(1000,3));
        errors.addNotRetryableExceptions(IllegalArgumentException.class);
        factory.setCommonErrorHandler(errors);factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);return factory;
    }
}
