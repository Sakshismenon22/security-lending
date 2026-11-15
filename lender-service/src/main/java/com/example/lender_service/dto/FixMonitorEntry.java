package com.example.lender_service.dto;

import java.time.Instant;

/** Gateway event representation for LendDesk FIX Monitor; raw FIX is owned by the adapter. */
public record FixMonitorEntry(
    String id,
    String eventId,
    String direction,
    Instant createdAt,
    String sender,
    String target,
    String messageType,
    String reference,
    String state,
    String detail) {}
