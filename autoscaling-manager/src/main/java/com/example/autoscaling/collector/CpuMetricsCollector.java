package com.example.autoscaling.collector;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.OptionalDouble;

/**
 * Reads the process.cpu.usage measurement exposed by the target service's Actuator endpoint.
 */
@Component
public class CpuMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(CpuMetricsCollector.class);

    private final RestClient restClient;
    private final String metricPath;

    public CpuMetricsCollector(
            RestClient.Builder restClientBuilder,
            @Value("${autoscaling.targets.borrower.base-url}") String baseUrl,
            @Value("${autoscaling.targets.borrower.cpu-metric-path}") String metricPath) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.metricPath = metricPath;
    }

    public OptionalDouble readBorrowerProcessCpuUsage() {
        try {
            JsonNode response = restClient.get()
                    .uri(metricPath)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null || !response.has("measurements")) {
                log.warn("Borrower CPU metric response did not contain measurements.");
                return OptionalDouble.empty();
            }

            JsonNode measurements = response.path("measurements");
            for (JsonNode measurement : measurements) {
                if ("VALUE".equalsIgnoreCase(measurement.path("statistic").asText())
                        && measurement.path("value").isNumber()) {
                    double value = measurement.path("value").asDouble();
                    if (Double.isFinite(value) && value >= 0.0) {
                        return OptionalDouble.of(value);
                    }
                }
            }

            log.warn("Borrower CPU metric response did not contain a valid VALUE measurement.");
            return OptionalDouble.empty();
        } catch (Exception exception) {
            log.warn("Unable to read Borrower Service CPU metric: {}", exception.getMessage());
            return OptionalDouble.empty();
        }
    }
}
