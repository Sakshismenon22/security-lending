package com.example.borrower_service.autoscaling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Periodically evaluates Borrower Service JVM CPU usage.
 * Consecutive breaches prevent a single short spike from triggering scale-out.
 */
@Component
public class CpuAutoscalingMonitor {

    private static final Logger log = LoggerFactory.getLogger(CpuAutoscalingMonitor.class);

    private final CpuMetricsCollector metricsCollector;
    private final CpuScalingDecisionEngine decisionEngine;
    private final double threshold;
    private final int requiredConsecutiveBreaches;
    private int consecutiveBreaches;

    public CpuAutoscalingMonitor(
            CpuMetricsCollector metricsCollector,
            CpuScalingDecisionEngine decisionEngine,
            org.springframework.core.env.Environment environment) {
        this.metricsCollector = metricsCollector;
        this.decisionEngine = decisionEngine;
        this.threshold = environment.getProperty("autoscaling.cpu.threshold", Double.class, 0.80);
        this.requiredConsecutiveBreaches = environment.getProperty(
                "autoscaling.cpu.consecutive-breaches", Integer.class, 3);
    }

    @Scheduled(fixedDelayString = "${autoscaling.cpu.evaluation-interval-ms:15000}")
    public void evaluateCpuUsage() {
        OptionalDouble cpu = metricsCollector.getProcessCpuUsage();
        if (cpu.isEmpty()) {
            log.debug("process.cpu.usage is not available yet; skipping this evaluation.");
            consecutiveBreaches = 0;
            return;
        }

        double usage = cpu.getAsDouble();
        if (decisionEngine.shouldScaleOut(usage, threshold)) {
            consecutiveBreaches++;
            log.info("Borrower JVM CPU usage is {}% (threshold {}%). Breach {}/{}.",
                    Math.round(usage * 100.0), Math.round(threshold * 100.0),
                    consecutiveBreaches, requiredConsecutiveBreaches);

            if (consecutiveBreaches >= requiredConsecutiveBreaches) {
                log.warn("CPU scale-out condition reached for Borrower Service. "
                        + "A Process Manager must handle instance creation.");
                consecutiveBreaches = 0;
            }
        } else {
            consecutiveBreaches = 0;
            log.debug("Borrower JVM CPU usage is {}%; below the {}% scale-out threshold.",
                    Math.round(usage * 100.0), Math.round(threshold * 100.0));
        }
    }
}
