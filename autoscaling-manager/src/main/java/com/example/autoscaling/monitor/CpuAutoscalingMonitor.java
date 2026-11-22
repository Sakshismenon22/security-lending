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
 * CPU-based autoscaling with separate scale-out and scale-in thresholds.
 * Scale-in only removes manager-owned secondary JVMs and preserves the baseline.
 */
@Component
public class CpuAutoscalingMonitor {

    private static final Logger log = LoggerFactory.getLogger(CpuAutoscalingMonitor.class);

    private final CpuMetricsCollector metricsCollector;
    private final CpuScalingDecisionEngine decisionEngine;
    private final ProcessManager processManager;
    private final double scaleOutThreshold;
    private final double scaleInThreshold;
    private final int requiredConsecutiveBreaches;
    private final int requiredConsecutiveLowReadings;
    private final Duration cooldown;
    private int consecutiveHighReadings;
    private int consecutiveLowReadings;
    private Instant lastScalingActionAt;

    public CpuAutoscalingMonitor(
            CpuMetricsCollector metricsCollector,
            CpuScalingDecisionEngine decisionEngine,
            ProcessManager processManager,
            @Value("${autoscaling.cpu.threshold:0.80}") double scaleOutThreshold,
            @Value("${autoscaling.cpu.scale-in-threshold:0.30}") double scaleInThreshold,
            @Value("${autoscaling.cpu.consecutive-breaches:3}") int requiredConsecutiveBreaches,
            @Value("${autoscaling.cpu.consecutive-low-readings:5}") int requiredConsecutiveLowReadings,
            @Value("${autoscaling.cpu.cooldown-ms:60000}") long cooldownMs) {
        if (scaleOutThreshold <= 0.0 || scaleOutThreshold > 1.0
                || scaleInThreshold < 0.0 || scaleInThreshold >= scaleOutThreshold) {
            throw new IllegalArgumentException("CPU scale-in threshold must be non-negative and lower than the scale-out threshold.");
        }
        if (requiredConsecutiveBreaches < 1 || requiredConsecutiveLowReadings < 1 || cooldownMs < 0) {
            throw new IllegalArgumentException("Consecutive reading counts must be positive and cooldown cannot be negative.");
        }
        this.metricsCollector = metricsCollector;
        this.decisionEngine = decisionEngine;
        this.processManager = processManager;
        this.scaleOutThreshold = scaleOutThreshold;
        this.scaleInThreshold = scaleInThreshold;
        this.requiredConsecutiveBreaches = requiredConsecutiveBreaches;
        this.requiredConsecutiveLowReadings = requiredConsecutiveLowReadings;
        this.cooldown = Duration.ofMillis(cooldownMs);
    }

    @Scheduled(fixedDelayString = "${autoscaling.cpu.evaluation-interval-ms:15000}")
    public synchronized void evaluateBorrowerCpuUsage() {
        if (!processManager.isBaselineReady()) {
            log.info("Waiting for the configured Borrower baseline to register with Eureka before CPU autoscaling.");
            return;
        }

        OptionalDouble cpuUsage = metricsCollector.readBorrowerProcessCpuUsage();
        if (cpuUsage.isEmpty()) {
            resetCounters();
            return;
        }

        double currentUsage = cpuUsage.getAsDouble();
        log.info("Highest Borrower process CPU usage={}%; scale-out threshold={}%; scale-in threshold={}%.",
                String.format("%.3f", currentUsage * 100.0),
                String.format("%.2f", scaleOutThreshold * 100.0),
                String.format("%.2f", scaleInThreshold * 100.0));

        if (decisionEngine.shouldScaleOut(currentUsage, scaleOutThreshold)) {
            consecutiveLowReadings = 0;
            consecutiveHighReadings++;
            log.info("Scale-out threshold breach {}/{}.", consecutiveHighReadings, requiredConsecutiveBreaches);
            if (consecutiveHighReadings >= requiredConsecutiveBreaches) {
                performScaleOut();
            }
            return;
        }

        consecutiveHighReadings = 0;
        if (currentUsage <= scaleInThreshold) {
            consecutiveLowReadings++;
            log.info("Low CPU reading {}/{} required for scale-in.",
                    consecutiveLowReadings, requiredConsecutiveLowReadings);
            if (consecutiveLowReadings >= requiredConsecutiveLowReadings) {
                performScaleIn();
            }
        } else {
            consecutiveLowReadings = 0;
            log.info("CPU is between scale-in and scale-out thresholds; keeping current capacity.");
        }
    }

    private void performScaleOut() {
        resetCounters();
        if (cooldownActive()) {
            log.info("Scale-out condition met, but autoscaling cooldown is active.");
            return;
        }

        log.warn("Borrower CPU remained above threshold. Requesting one additional JVM instance.");
        boolean started = processManager.scaleOut();
        lastScalingActionAt = Instant.now();
        if (started) {
            log.info("Borrower Service scale-out completed successfully.");
        } else {
            log.warn("Borrower Service scale-out was not completed. Check Process Manager logs.");
        }
    }

    private void performScaleIn() {
        resetCounters();
        if (cooldownActive()) {
            log.info("Scale-in condition met, but autoscaling cooldown is active.");
            return;
        }

        log.warn("All Borrower instances have remained below the scale-in threshold. Requesting one managed JVM to stop.");
        boolean stopped = processManager.scaleIn();
        lastScalingActionAt = Instant.now();
        if (stopped) {
            log.info("Borrower Service scale-in completed successfully.");
        } else {
            log.info("Scale-in was not performed; the configured baseline or process ownership rules prevent it.");
        }
    }

    private boolean cooldownActive() {
        return lastScalingActionAt != null && Instant.now().isBefore(lastScalingActionAt.plus(cooldown));
    }

    private void resetCounters() {
        consecutiveHighReadings = 0;
        consecutiveLowReadings = 0;
    }
}
