package com.example.borrower_service.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Local-only CPU load generator for validating the Borrower autoscaling policy.
 * Disabled unless autoscaling.test.cpu-load.enabled=true.
 */
@RestController
@RequestMapping("/internal/autoscaling/load-test")
@ConditionalOnProperty(name = "autoscaling.test.cpu-load.enabled", havingValue = "true")
public class AutoscalingLoadTestController {

    private static final AtomicLong BLACK_HOLE = new AtomicLong();

    @Value("${autoscaling.test.cpu-load.max-duration-seconds:60}")
    private int maxDurationSeconds;

    @Value("${autoscaling.test.cpu-load.max-workers:4}")
    private int maxWorkers;

    @PostMapping("/cpu")
    public Map<String, Object> generateCpuLoad(
            @RequestParam(defaultValue = "30") int durationSeconds,
            @RequestParam(defaultValue = "2") int workers,
            jakarta.servlet.http.HttpServletRequest request) {

        requireLoopback(request.getRemoteAddr());

        if (durationSeconds < 1 || durationSeconds > maxDurationSeconds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "durationSeconds must be between 1 and " + maxDurationSeconds + ".");
        }
        if (workers < 1 || workers > maxWorkers) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "workers must be between 1 and " + maxWorkers + ".");
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
        AtomicLong iterations = new AtomicLong();
        var executor = Executors.newFixedThreadPool(workers);
        try {
            java.util.List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, workers)
                    .mapToObj(worker -> (Callable<Void>) () -> {
                        long local = worker + 1L;
                        while (System.nanoTime() < deadline) {
                            // Deterministic arithmetic work that cannot be eliminated as dead code.
                            local = (local * 1_664_525L + 1_013_904_223L) ^ (local >>> 13);
                            iterations.incrementAndGet();
                        }
                        BLACK_HOLE.addAndGet(local);
                        return null;
                    }).toList();

            for (Future<Void> future : executor.invokeAll(tasks, durationSeconds + 5L, TimeUnit.SECONDS)) {
                future.get();
            }
        } catch (Exception exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "CPU load test did not complete successfully.", exception);
        } finally {
            executor.shutdownNow();
        }

        return Map.of(
                "status", "COMPLETED",
                "instancePort", Integer.parseInt(System.getProperty("server.port", "8081")),
                "durationSeconds", durationSeconds,
                "workers", workers,
                "iterations", iterations.get());
    }

    private void requireLoopback(String remoteAddress) {
        try {
            if (!InetAddress.getByName(remoteAddress).isLoopbackAddress()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "CPU load testing is restricted to localhost.");
            }
        } catch (java.net.UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Unable to verify the request origin.", exception);
        }
    }
}
