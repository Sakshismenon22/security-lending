package com.example.borrower_service.autoscaling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import org.springframework.stereotype.Component;

import java.util.OptionalDouble;

/**
 * Reads the CPU usage of this Borrower Service JVM from Micrometer.
 * process.cpu.usage is a fraction: 0.80 represents 80% of one CPU's capacity.
 */
@Component
public class CpuMetricsCollector {

    private final MeterRegistry meterRegistry;

    public CpuMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public OptionalDouble getProcessCpuUsage() {
        Gauge gauge = meterRegistry.find("process.cpu.usage").gauge();
        if (gauge == null) {
            return OptionalDouble.empty();
        }

        double usage = gauge.value();
        if (!Double.isFinite(usage) || usage < 0.0) {
            return OptionalDouble.empty();
        }

        return OptionalDouble.of(usage);
    }
}
