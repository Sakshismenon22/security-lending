package com.example.autoscaling.process;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reads Borrower Service registrations from Eureka.
 * ProcessManager separately retains local PID ownership information.
 */
@Component
public class InstanceRegistry {

    private static final String BORROWER_SERVICE_ID = "borrower-service";

    private final DiscoveryClient discoveryClient;
    private final ProcessManager processManager;

    public InstanceRegistry(DiscoveryClient discoveryClient, ProcessManager processManager) {
        this.discoveryClient = discoveryClient;
        this.processManager = processManager;
    }

    public List<RegisteredInstance> getBorrowerInstances() {
        return discoveryClient.getInstances(BORROWER_SERVICE_ID).stream()
                .sorted(Comparator.comparingInt(ServiceInstance::getPort))
                .map(instance -> new RegisteredInstance(
                        instance.getInstanceId(),
                        instance.getServiceId(),
                        instance.getHost(),
                        instance.getPort(),
                        instance.getUri().toString(),
                        Map.copyOf(instance.getMetadata())))
                .toList();
    }

    public List<ManagedInstance> getManagedBorrowerProcesses() {
        return processManager.getInstances();
    }
}
