package com.example.autoscaling.monitor;

import com.example.autoscaling.collector.CpuMetricsCollector;
import com.example.autoscaling.decision.CpuScalingDecisionEngine;
import com.example.autoscaling.process.ProcessManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalDouble;

/**
 * Evaluates Borrower CPU usage and asks ProcessManager to create an instance
 * after the configured number of consecutive threshold breaches.
 */
@Component
public class CpuAutoscalingMonitor {

    private static final Logger log = LoggerFactory.getLogger(CpuAutoscalingMonitor.class);

    private final CpuMetricsCollector metricsCollector;
    private final CpuScalingDecisionEngine decisionEngine;
    private final ProcessManager processManager;
    private final double threshold;
    private final int requiredConsecutiveBreaches;
    private final Duration cooldown;
    private int consecutiveBreaches;
    private Instant lastScaleOutAt;

    public CpuAutoscalingMonitor(
            CpuMetricsCollector metricsCollector,
            CpuScalingDecisionEngine decisionEngine,
            ProcessManager processManager,
            @Value("${autoscaling.cpu.threshold:0.80}") double threshold,
            @Value("${autoscaling.cpu.consecutive-breaches:3}") int requiredConsecutiveBreaches,
            @Value("${autoscaling.cpu.cooldown-ms:60000}") long cooldownMs) {
        if (threshold <= 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("autoscaling.cpu.threshold must be greater than 0 and at most 1.");
        }
        if (requiredConsecutiveBreaches < 1 || cooldownMs < 0) {
            throw new IllegalArgumentException("Consecutive breaches must be positive and cooldown cannot be negative.");
        }
        this.metricsCollector = metricsCollector;
        this.decisionEngine = decisionEngine;
        this.processManager = processManager;
        this.threshold = threshold;
        this.requiredConsecutiveBreaches = requiredConsecutiveBreaches;
        this.cooldown = Duration.ofMillis(cooldownMs);
    }

    @Scheduled(fixedDelayString = "${autoscaling.cpu.evaluation-interval-ms:15000}")
    public synchronized void evaluateBorrowerCpuUsage() {
        OptionalDouble cpuUsage = metricsCollector.readBorrowerProcessCpuUsage();
        if (cpuUsage.isEmpty()) {
            consecutiveBreaches = 0;
            return;
        }

        double currentUsage = cpuUsage.getAsDouble();
        if (!decisionEngine.shouldScaleOut(currentUsage, threshold)) {
            consecutiveBreaches = 0;
            log.info("Borrower process CPU usage={}%; scale-out threshold={}%; no scale-out required.",
                    Math.round(currentUsage * 100.0),
                    Math.round(threshold * 100.0));
            return;
        }

        consecutiveBreaches++;
        log.info("Borrower process CPU usage={}%, threshold={}%, consecutive breaches={}/{}",
                Math.round(currentUsage * 100.0),
                Math.round(threshold * 100.0),
                consecutiveBreaches,
                requiredConsecutiveBreaches);

        if (consecutiveBreaches < requiredConsecutiveBreaches) {
            return;
        }

        consecutiveBreaches = 0;
        if (lastScaleOutAt != null && Instant.now().isBefore(lastScaleOutAt.plus(cooldown))) {
            log.info("CPU threshold remains exceeded, but scale-out cooldown is active.");
            return;
        }

        log.warn("Borrower Service CPU threshold reached. Requesting one additional JVM instance.");
        boolean started = processManager.scaleOut();
        if (started) {
            lastScaleOutAt = Instant.now();
            log.info("Borrower Service scale-out completed successfully.");
        } else {
            log.warn("Borrower Service scale-out was not completed. Check Process Manager logs.");
        }
    }
}
