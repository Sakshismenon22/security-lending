package com.example.fix_adapter_service.dto;

public record EnqueueResult(String eventId,boolean duplicate,boolean skipped) {}
