package com.example.fix_adapter_service.dto;

public record RoutedEnvelope(ValidatedEnvelope envelope,String sessionKey,String counterpartyId) {}
