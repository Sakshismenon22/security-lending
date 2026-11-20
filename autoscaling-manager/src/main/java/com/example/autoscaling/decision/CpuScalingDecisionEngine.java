package com.example.autoscaling.decision;

import org.springframework.stereotype.Component;

/**
 * Evaluates the CPU threshold. Process creation is deliberately handled by a separate component.
 */
@Component
public class CpuScalingDecisionEngine {

    public boolean shouldScaleOut(double cpuUsage, double threshold) {
        return Double.isFinite(cpuUsage) && cpuUsage >= threshold;
    }
}
