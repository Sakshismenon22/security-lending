package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.dto.*;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class OutboundQueueService {
    private final AdapterIdentityService identity;
    private final AdapterIdentityRepository identities;
    private final OutboundMessageRepository messages;
    private final EnvelopeService envelopes;
    private final AdapterProperties properties;
    private final Clock clock;
    @Transactional
    public EnqueueResult enqueue(String body,SourceRecord source) {
        if(!source.topic().equals(properties.getOutboundTopic()))throw new IllegalArgumentException("Unexpected source topic");
        var routed=envelopes.outbound(body);
        if(routed.isEmpty())return new EnqueueResult(null,false,true);
        identities.lockIdentity().orElseThrow();
        var route=routed.get();var envelope=route.envelope();
        var existing=messages.findByEventId(envelope.eventId());
        if(existing.isPresent()) {
            var old=existing.get();
            if(!old.getPayloadHash().equals(envelope.payloadHash()) || !old.getSessionKey().equals(route.sessionKey()))throw new IllegalArgumentException("Event ID was reused with different content or routing");
            return new EnqueueResult(envelope.eventId(),true,false);
        }
        var message=new OutboundMessage();message.setEventId(envelope.eventId());message.setEventType(envelope.eventType());
        message.setOrganizationId(envelope.organizationId());message.setReferenceId(envelope.referenceId());message.setMessageKind(envelope.kind());
        message.setSessionKey(route.sessionKey());message.setCounterpartyId(route.counterpartyId());message.setPayload(envelope.payload());message.setPayloadHash(envelope.payloadHash());
        message.setState(DeliveryState.QUEUED);message.setNextAttemptAt(clock.instant());message.setCreatedAt(clock.instant());
        message.setSourceTopic(source.topic());message.setSourcePartition(source.partition());message.setSourceOffset(source.offset());messages.saveAndFlush(message);
        return new EnqueueResult(envelope.eventId(),false,false);
    }
}
