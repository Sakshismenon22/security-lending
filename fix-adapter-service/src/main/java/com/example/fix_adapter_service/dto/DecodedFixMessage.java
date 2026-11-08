package com.example.fix_adapter_service.dto;

import com.example.fix_adapter_service.model.MessageKind;

public record DecodedFixMessage(MessageKind kind,String eventId,String payloadHash,String payload) {}
