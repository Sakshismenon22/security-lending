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
public class InboundQueueService {
    private final AdapterIdentityService identity;
    private final AdapterIdentityRepository identities;
    private final InboundMessageRepository messages;
    private final KafkaPublicationRepository publications;
    private final EnvelopeService envelopes;
    private final AdapterProperties properties;
    private final Clock clock;
    @Transactional
    public void receive(DecodedFixMessage decoded,String sessionKey) {
        var routed=envelopes.inbound(decoded,sessionKey);var envelope=routed.envelope();
        identities.lockIdentity().orElseThrow();
        var existing=messages.findByEventId(envelope.eventId());
        if(existing.isPresent()) {
            var previous=existing.get();
            if(!previous.getPayloadHash().equals(envelope.payloadHash()) || !previous.getSessionKey().equals(sessionKey))throw new IllegalArgumentException("Inbound event ID conflicts with a previously persisted message");
            return;
        }
        var inbound=new InboundMessage();inbound.setEventId(envelope.eventId());inbound.setEventType(envelope.eventType());
        inbound.setOrganizationId(envelope.organizationId());inbound.setReferenceId(envelope.referenceId());inbound.setMessageKind(envelope.kind());
        inbound.setSessionKey(sessionKey);inbound.setCounterpartyId(routed.counterpartyId());inbound.setPayload(envelope.payload());inbound.setPayloadHash(envelope.payloadHash());inbound.setReceivedAt(clock.instant());messages.saveAndFlush(inbound);
        var publication=new KafkaPublication();publication.setInboundMessageId(inbound.getId());publication.setEventId(envelope.eventId());
        publication.setTopic(properties.getInboundTopic());publication.setPartitionKey(String.valueOf(envelope.organizationId()));publication.setPayload(envelope.payload());
        publication.setState(PublicationState.PENDING);publication.setNextAttemptAt(clock.instant());publication.setCreatedAt(clock.instant());publications.saveAndFlush(publication);
    }
}
