package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.repository.*;
import com.example.fix_adapter_service.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Clock;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class AdapterOperationsService {
    private final OutboundMessageRepository outbound;private final InboundMessageRepository inbound;private final KafkaPublicationRepository publications;
    private final AdapterProperties properties;private final FixEngineService engine;private final Clock clock;
    public Object status() {return Map.of("side",properties.getSide(),"institutionId",properties.getInstitutionId(),"fixEnabled",properties.isFixEnabled(),"kafkaEnabled",properties.isKafkaEnabled(),"sessions",engine.sessions(),"awaitingReceipt",outbound.countByState(DeliveryState.AWAITING_RECEIPT),"suspendedOutbound",outbound.countByState(DeliveryState.SUSPENDED),"pendingPublications",publications.countByState(PublicationState.PENDING),"suspendedPublications",publications.countByState(PublicationState.SUSPENDED));}
    private Pageable page(int page,int size) {if(page<0 || size<1 || size>200)throw new IllegalArgumentException("page must be nonnegative; size must be between 1 and 200");return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"id"));}
    public Object outbound(int page,int size) {return PageResponse.from(outbound.findAll(page(page,size)));}
    public Object inbound(int page,int size) {return PageResponse.from(inbound.findAll(page(page,size)));}
    public Object publications(int page,int size) {return PageResponse.from(publications.findAll(page(page,size)));}
    @Transactional public OutboundMessage retryOutbound(long id) {
        var message=outbound.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Outbound message not found"));
        if(message.getState()==DeliveryState.ACKNOWLEDGED)throw new ResponseStatusException(HttpStatus.CONFLICT,"An acknowledged message cannot be resent manually");
        message.setState(DeliveryState.QUEUED);message.setAttempts(0);message.setNextAttemptAt(clock.instant());message.setLastError(null);return message;
    }
    @Transactional public KafkaPublication retryPublication(long id) {
        var publication=publications.lockById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Publication not found"));
        if(publication.getState()==PublicationState.PUBLISHED)throw new ResponseStatusException(HttpStatus.CONFLICT,"A published message cannot be republished manually");
        publication.setState(PublicationState.PENDING);publication.setAttempts(0);publication.setNextAttemptAt(clock.instant());publication.setLastError(null);return publication;
    }
}
