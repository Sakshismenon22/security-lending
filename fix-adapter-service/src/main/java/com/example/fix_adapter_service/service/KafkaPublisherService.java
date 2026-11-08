package com.example.fix_adapter_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class KafkaPublisherService implements BusinessEventPublisher {
    private final KafkaTemplate<String,String> kafka;
    @Override public void publish(String topic,String key,String payload) throws Exception {kafka.send(topic,key,payload).get(20,TimeUnit.SECONDS);}
    @Override public void publish(String topic,String key,String payload,java.util.Map<String,String> headers) throws Exception {
        var record=new org.apache.kafka.clients.producer.ProducerRecord<String,String>(topic,key,payload);
        headers.forEach((name,value)->record.headers().add(name,value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        kafka.send(record).get(20,TimeUnit.SECONDS);
    }
}
