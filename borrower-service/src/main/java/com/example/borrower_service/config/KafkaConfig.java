package com.example.borrower_service.config;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
@ConditionalOnProperty(name="borrower.kafka-enabled",havingValue="true")
public class KafkaConfig {
    @Bean public ConcurrentKafkaListenerContainerFactory<String,String> kafkaListenerContainerFactory(ConsumerFactory<String,String> consumerFactory,KafkaTemplate<String,String> kafka) {
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();factory.setConsumerFactory(consumerFactory);
        var recoverer=new DeadLetterPublishingRecoverer(kafka,(record,error)->new org.apache.kafka.common.TopicPartition(record.topic()+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer,new FixedBackOff(1000,3)));
        factory.getContainerProperties().setAckMode(org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD);
        return factory;
    }
}
