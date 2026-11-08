package com.example.fix_adapter_service.dto;

import com.example.fix_adapter_service.model.MessageKind;

public record ValidatedEnvelope(String eventId,String eventType,long organizationId,long referenceId,
                                MessageKind kind,String payload,String payloadHash) {}
