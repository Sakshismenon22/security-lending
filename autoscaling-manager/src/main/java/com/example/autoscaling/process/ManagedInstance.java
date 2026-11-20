package com.example.autoscaling.process;

import java.time.Instant;

/** Runtime details for a Borrower Service JVM started by this manager. */
public record ManagedInstance(
        long processId,
        int port,
        Instant startedAt,
        String status) {
}
