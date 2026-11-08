package com.example.fix_adapter_service.dto;

public record SourceRecord(String topic,int partition,long offset) {}
