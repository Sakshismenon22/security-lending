package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.dto.SourceRecord;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name="adapter.kafka-enabled",havingValue="true")
public class KafkaIngressService {
    private final OutboundQueueService outbound;
    @KafkaListener(topics="${adapter.outbound-topic}",groupId="${adapter.consumer-group}")
    public void receive(ConsumerRecord<String,String> record) {outbound.enqueue(record.value(),new SourceRecord(record.topic(),record.partition(),record.offset()));}
}
