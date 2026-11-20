package com.example.borrower_service.autoscaling;

import org.springframework.stereotype.Component;

/**
 * Applies the initial CPU scale-out rule.
 * This class makes a decision only; it does not start or stop JVM processes.
 */
@Component
public class CpuScalingDecisionEngine {

    public boolean shouldScaleOut(double cpuUsage, double threshold) {
        return Double.isFinite(cpuUsage) && cpuUsage >= threshold;
    }
}
