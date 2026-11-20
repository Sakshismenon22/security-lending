package com.example.autoscaling.monitor;

import com.example.autoscaling.collector.CpuMetricsCollector;
import com.example.autoscaling.decision.CpuScalingDecisionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Periodically evaluates Borrower Service CPU usage and identifies scale-out conditions.
 * This first stage reports the decision; it does not start additional JVMs yet.
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
            @Value("${autoscaling.cpu.threshold:0.80}") double threshold,
            @Value("${autoscaling.cpu.consecutive-breaches:3}") int requiredConsecutiveBreaches) {
        if (threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("autoscaling.cpu.threshold must be greater than 0 and at most 1.");
        }
        if (requiredConsecutiveBreaches < 1) {
            throw new IllegalArgumentException("autoscaling.cpu.consecutive-breaches must be at least 1.");
        }
        this.metricsCollector = metricsCollector;
        this.decisionEngine = decisionEngine;
        this.threshold = threshold;
        this.requiredConsecutiveBreaches = requiredConsecutiveBreaches;
    }

    @Scheduled(fixedDelayString = "${autoscaling.cpu.evaluation-interval-ms:15000}")
    public void evaluateBorrowerCpuUsage() {
        OptionalDouble cpuUsage = metricsCollector.readBorrowerProcessCpuUsage();
        if (cpuUsage.isEmpty()) {
            consecutiveBreaches = 0;
            return;
        }

        double currentUsage = cpuUsage.getAsDouble();
        if (decisionEngine.shouldScaleOut(currentUsage, threshold)) {
            consecutiveBreaches++;
            log.info("Borrower process CPU usage={}%, threshold={}%, consecutive breaches={}/{}",
                    Math.round(currentUsage * 100.0),
                    Math.round(threshold * 100.0),
                    consecutiveBreaches,
                    requiredConsecutiveBreaches);

            if (consecutiveBreaches >= requiredConsecutiveBreaches) {
                log.warn("Borrower Service meets the CPU scale-out condition. "
                        + "Process Manager integration is the next implementation stage.");
                consecutiveBreaches = 0;
            }
        } else {
            consecutiveBreaches = 0;
            log.info("Borrower process CPU usage={}%; scale-out threshold={}%; no scale-out required.",
                    Math.round(currentUsage * 100.0),
                    Math.round(threshold * 100.0));
        }
    }
}
