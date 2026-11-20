package com.example.autoscaling.controller;

import com.example.autoscaling.process.InstanceRegistry;
import com.example.autoscaling.process.ManagedInstance;
import com.example.autoscaling.process.RegisteredInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exposes Eureka registrations and locally managed Borrower JVM processes. */
@RestController
@RequestMapping("/api/autoscaling")
public class AutoscalingController {

    private final InstanceRegistry instanceRegistry;

    public AutoscalingController(InstanceRegistry instanceRegistry) {
        this.instanceRegistry = instanceRegistry;
    }

    /** Instances currently advertised by Eureka. */
    @GetMapping("/borrower/instances")
    public List<RegisteredInstance> borrowerInstances() {
        return instanceRegistry.getBorrowerInstances();
    }

    /** Child JVM processes started and owned by this Autoscaling Manager. */
    @GetMapping("/borrower/processes")
    public List<ManagedInstance> borrowerProcesses() {
        return instanceRegistry.getManagedBorrowerProcesses();
    }
}
