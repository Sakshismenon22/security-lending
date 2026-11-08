package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.config.AdapterProperties;
import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.repository.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class OutboundDeliveryService {
    private final OutboundMessageRepository messages;private final FixTransport transport;private final FixMessageCodec codec;
    private final AdapterProperties properties;private final Clock clock;
    @Transactional
    public void deliverNext(String sessionKey) {
        var pending=messages.lockHead(sessionKey,PageRequest.of(0,1));if(pending.isEmpty())return;
        var message=pending.get(0);var now=clock.instant();
        if(message.getState()==DeliveryState.SUSPENDED || message.getNextAttemptAt().isAfter(now))return;
        if(!transport.loggedOn(sessionKey)) {message.setLastError("FIX session is not logged on");message.setNextAttemptAt(now.plusSeconds(properties.getRetrySeconds()));return;}
        if(message.getAttempts()>=properties.getMaxDeliveryAttempts()) {message.setState(DeliveryState.SUSPENDED);message.setLastError("No durable peer receipt after maximum attempts; inspect the peer and retry explicitly");return;}
        message.setAttempts(message.getAttempts()+1);
        try {
            if(!transport.send(sessionKey,codec.business(message)))throw new IllegalStateException("FIX send returned false");
            message.setState(DeliveryState.AWAITING_RECEIPT);message.setLastSentAt(now);message.setLastError(null);
            message.setNextAttemptAt(now.plusSeconds(properties.getReceiptTimeoutSeconds()));
        } catch(Exception e) {message.setLastError(safeError(e));message.setNextAttemptAt(now.plusSeconds(properties.getRetrySeconds()));}
    }
    static String safeError(Exception e) {String value=e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage());return value.substring(0,Math.min(1000,value.length()));}
}
