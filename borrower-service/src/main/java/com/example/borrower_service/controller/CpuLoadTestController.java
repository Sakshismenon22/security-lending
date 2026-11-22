package com.example.borrower_service.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/**
 * Local-only CPU load generator for validating the Borrower JVM autoscaling flow.
 * This endpoint is disabled unless autoscaling.test.cpu-load.enabled=true.
 */
@RestController
@RequestMapping("/internal/autoscaling-test")
@ConditionalOnProperty(name = "autoscaling.test.cpu-load.enabled", havingValue = "true")
public class CpuLoadTestController {

    private static final int MAX_DURATION_SECONDS = 90;
    private static final int MAX_WORKERS = 4;

    private final int serverPort;
    private final ExecutorService workers = Executors.newFixedThreadPool(MAX_WORKERS, runnable -> {
        Thread thread = new Thread(runnable, "borrower-cpu-load-test");
        thread.setDaemon(true);
        return thread;
    });
    private final Semaphore workerPermits = new Semaphore(MAX_WORKERS);

    public CpuLoadTestController(@Value("${server.port:8081}") int serverPort) {
        this.serverPort = serverPort;
    }

    @GetMapping("/cpu-load")
    public CpuLoadResult generateCpuLoad(
            @RequestParam(defaultValue = "20") int durationSeconds,
            @RequestParam(defaultValue = "2") int workerCount,
            HttpServletRequest request) {

        if (!isLoopbackRequest(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "CPU load testing is available only from the local machine.");
        }
        if (durationSeconds < 1 || durationSeconds > MAX_DURATION_SECONDS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "durationSeconds must be between 1 and " + MAX_DURATION_SECONDS + ".");
        }
        if (workerCount < 1 || workerCount > MAX_WORKERS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "workers must be between 1 and " + MAX_WORKERS + ".");
        }
        if (!workerPermits.tryAcquire(workerCount)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "CPU load test capacity is busy. Retry after the current test finishes.");
        }

        long deadline = System.nanoTime() + durationSeconds * 1_000_000_000L;
        List<Future<Long>> tasks = new ArrayList<>();
        try {
            for (int i = 0; i < workerCount; i++) {
                tasks.add(workers.submit(() -> burnCpuUntil(deadline)));
            }

            long totalIterations = 0;
            for (Future<Long> task : tasks) {
                totalIterations += task.get();
            }

            return new CpuLoadResult("COMPLETED", serverPort, durationSeconds,
                    workerCount, totalIterations);
        } catch (InterruptedException interrupted) {
            tasks.forEach(task -> task.cancel(true));
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "CPU load test was interrupted.");
        } catch (ExecutionException failure) {
            tasks.forEach(task -> task.cancel(true));
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "CPU load worker failed.", failure.getCause());
        } finally {
            workerPermits.release(workerCount);
        }
    }

    private long burnCpuUntil(long deadline) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = new byte[4096];
            long iterations = 0;
            while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
                input = digest.digest(input);
                iterations++;
            }
            return iterations;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable.", impossible);
        }
    }

    private boolean isLoopbackRequest(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (Exception ignored) {
            return false;
        }
    }

    public record CpuLoadResult(
            String status,
            int instancePort,
            int durationSeconds,
            int workers,
            long iterations) {
    }
}
