package com.example.fix_adapter_service.dto;

public record SessionStatus(String sessionKey,String counterpartyId,boolean enabled,boolean loggedOn,
                            Integer nextSenderSequence,Integer nextTargetSequence) {}
