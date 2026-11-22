package com.example.autoscaling.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Reads process CPU usage from every Borrower JVM registered with Eureka.
 * The highest observed per-process CPU value is used as the scale-out signal.
 */
@Component
public class CpuMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(CpuMetricsCollector.class);

    private final DiscoveryClient discoveryClient;
    private final String serviceId;
    private final String metricPath;

    public CpuMetricsCollector(
            DiscoveryClient discoveryClient,
            @Value("${autoscaling.targets.borrower.service-id:borrower-service}") String serviceId,
            @Value("${autoscaling.targets.borrower.cpu-metric-path:/actuator/metrics/process.cpu.usage}") String metricPath) {
        this.discoveryClient = discoveryClient;
        this.serviceId = serviceId;
        this.metricPath = metricPath;
    }

    public OptionalDouble readBorrowerProcessCpuUsage() {
        List<ServiceInstance> instances = discoveryClient.getInstances(serviceId);
        if (instances.isEmpty()) {
            log.warn("Eureka has no registered instances for {}.", serviceId);
            return OptionalDouble.empty();
        }

        double highestCpuUsage = -1.0;
        boolean measurementFound = false;

        for (ServiceInstance instance : instances) {
            try {
                Map response = RestClient.create(instance.getUri().toString())
                        .get()
                        .uri(metricPath)
                        .retrieve()
                        .body(Map.class);

                if (response == null || !(response.get("measurements") instanceof List<?> measurements)) {
                    log.warn("CPU metric response from {}:{} did not contain measurements.",
                            instance.getHost(), instance.getPort());
                    continue;
                }

                for (Object item : measurements) {
                    if (item instanceof Map<?, ?> measurement
                            && "VALUE".equalsIgnoreCase(String.valueOf(measurement.get("statistic")))
                            && measurement.get("value") instanceof Number value) {
                        double usage = value.doubleValue();
                        if (Double.isFinite(usage) && usage >= 0.0) {
                            highestCpuUsage = Math.max(highestCpuUsage, usage);
                            measurementFound = true;
                            log.debug("Borrower instance {}:{} CPU usage={}%.",
                                    instance.getHost(), instance.getPort(), usage * 100.0);
                        }
                    }
                }
            } catch (Exception exception) {
                log.warn("Unable to read CPU metric from Borrower instance {}:{}: {}",
                        instance.getHost(), instance.getPort(), exception.getMessage());
            }
        }

        if (!measurementFound) {
            return OptionalDouble.empty();
        }

        log.info("Highest Borrower instance CPU usage across {} registered instance(s) is {}%.",
                instances.size(), String.format("%.3f", highestCpuUsage * 100.0));
        return OptionalDouble.of(highestCpuUsage);
    }
}
