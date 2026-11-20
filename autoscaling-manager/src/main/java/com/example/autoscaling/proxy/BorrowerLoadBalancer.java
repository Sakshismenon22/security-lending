package com.example.autoscaling.proxy;

import com.example.autoscaling.process.ManagedInstance;
import com.example.autoscaling.process.ProcessManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Selects a healthy Borrower JVM using round-robin.
 * The original Borrower instance remains the fallback and first pool member.
 */
@Component
public class BorrowerLoadBalancer {

    private final ProcessManager processManager;
    private final int primaryPort;
    private final AtomicInteger cursor = new AtomicInteger();

    public BorrowerLoadBalancer(
            ProcessManager processManager,
            @Value("${autoscaling.targets.borrower.primary-port:8081}") int primaryPort) {
        this.processManager = processManager;
        this.primaryPort = primaryPort;
    }

    public int nextPort() {
        List<Integer> ports = new ArrayList<>();
        ports.add(primaryPort);
        for (ManagedInstance instance : processManager.getInstances()) {
            if ("RUNNING".equals(instance.status())) {
                ports.add(instance.port());
            }
        }
        int index = Math.floorMod(cursor.getAndIncrement(), ports.size());
        return ports.get(index);
    }
}
