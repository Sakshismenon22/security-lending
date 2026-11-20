package com.example.autoscaling.process;

import java.util.Map;

/** A Borrower Service instance as advertised by Eureka discovery. */
public record RegisteredInstance(
        String instanceId,
        String serviceId,
        String host,
        int port,
        String uri,
        Map<String, String> metadata) {
}
