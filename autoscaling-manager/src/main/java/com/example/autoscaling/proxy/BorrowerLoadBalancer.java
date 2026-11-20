package com.example.autoscaling.proxy;

import com.example.autoscaling.process.ManagedInstance;
import com.example.autoscaling.process.ProcessManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Selects a healthy Borrower JVM using round-robin, including the primary instance. */
@Component
public class BorrowerLoadBalancer {

    private final ProcessManager processManager;
    private final int primaryPort;
    private final AtomicInteger cursor = new AtomicInteger();
    private final HttpClient healthClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

    public BorrowerLoadBalancer(
            ProcessManager processManager,
            @Value("${autoscaling.targets.borrower.primary-port:8081}") int primaryPort) {
        this.processManager = processManager;
        this.primaryPort = primaryPort;
    }

    /** Returns a healthy instance port, or -1 if no candidate is healthy. */
    public int nextPort() {
        List<Integer> candidates = new ArrayList<>();
        candidates.add(primaryPort);
        for (ManagedInstance instance : processManager.getInstances()) {
            if ("RUNNING".equals(instance.status())) {
                candidates.add(instance.port());
            }
        }

        int start = Math.floorMod(cursor.getAndIncrement(), candidates.size());
        for (int offset = 0; offset < candidates.size(); offset++) {
            int port = candidates.get((start + offset) % candidates.size());
            if (isHealthy(port)) {
                return port;
            }
        }
        return -1;
    }

    private boolean isHealthy(int port) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create("http://127.0.0.1:" + port + "/actuator/health"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            HttpResponse<String> response = healthClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
        } catch (Exception ignored) {
            return false;
        }
    }
}
