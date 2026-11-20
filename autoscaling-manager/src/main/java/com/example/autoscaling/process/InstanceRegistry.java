package com.example.autoscaling.process;

import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only view of additional Borrower JVMs owned by this Autoscaling Manager. */
@Component
public class InstanceRegistry {

    private final ProcessManager processManager;

    public InstanceRegistry(ProcessManager processManager) {
        this.processManager = processManager;
    }

    public List<ManagedInstance> getBorrowerInstances() {
        return processManager.getInstances();
    }
}
