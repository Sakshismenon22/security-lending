package com.example.fix_adapter_service.config;

import com.example.fix_adapter_service.repository.*;
import com.example.fix_adapter_service.service.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="adapter.workers-enabled",havingValue="true",matchIfMissing=true)
public class AdapterWorkers {
    private static final Logger log=LoggerFactory.getLogger(AdapterWorkers.class);
    private final OutboundMessageRepository outbound;private final KafkaPublicationRepository publications;
    private final OutboundDeliveryService delivery;private final PublicationService publishing;
    @Scheduled(fixedDelayString="${adapter.delivery-poll-ms:1000}")
    public void deliver() {for(String session:outbound.pendingSessions())try{delivery.deliverNext(session);}catch(Exception e){log.error("FIX delivery worker failed for {}",session,e);}}
    @Scheduled(fixedDelayString="${adapter.publication-poll-ms:1000}")
    public void publish() {for(String partition:publications.pendingPartitions())try{publishing.publishNext(partition);}catch(Exception e){log.error("Kafka publication worker failed for partition {}",partition,e);}}
}
