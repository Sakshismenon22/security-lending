package com.example.autoscaling.controller;

import com.example.autoscaling.process.InstanceRegistry;
import com.example.autoscaling.process.ManagedInstance;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Exposes the additional Borrower JVMs currently managed by this process. */
@RestController
@RequestMapping("/api/autoscaling")
public class AutoscalingController {

    private final InstanceRegistry instanceRegistry;

    public AutoscalingController(InstanceRegistry instanceRegistry) {
        this.instanceRegistry = instanceRegistry;
    }

    @GetMapping("/borrower/instances")
    public List<ManagedInstance> borrowerInstances() {
        return instanceRegistry.getBorrowerInstances();
    }
}
