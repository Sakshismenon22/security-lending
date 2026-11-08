package com.example.fix_adapter_service.service;

import com.example.fix_adapter_service.model.*;
import com.example.fix_adapter_service.repository.OutboundMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ReceiptService {
    private final OutboundMessageRepository messages;
    private final Clock clock;
    @Transactional
    public void acknowledge(String eventId,String hash,String sessionKey) {
        var message=messages.lockByEventId(eventId).orElseThrow(()->new IllegalArgumentException("Receipt references an unknown outbound event"));
        if(!message.getSessionKey().equals(sessionKey) || !message.getPayloadHash().equals(hash))throw new IllegalArgumentException("Receipt does not match the original FIX session and payload");
        if(message.getState()!=DeliveryState.ACKNOWLEDGED) {
            message.setState(DeliveryState.ACKNOWLEDGED);message.setAcknowledgedAt(clock.instant());message.setLastError(null);
        }
    }
}
