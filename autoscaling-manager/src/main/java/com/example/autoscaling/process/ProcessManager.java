package com.example.autoscaling.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Starts and tracks additional local Borrower Service JVM processes.
 * This first version supports scale-out only; it never terminates processes.
 */
@Component
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);

    private final Path borrowerDirectory;
    private final Path borrowerJar;
    private final Path logDirectory;
    private final int firstPort;
    private final int lastPort;
    private final int maxInstances;
    private final int baselineInstances;
    private final Duration startupTimeout;
    private final DiscoveryClient discoveryClient;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final List<ManagedProcess> instances = new ArrayList<>();
    private volatile boolean baselineReady;

    public ProcessManager(
            @Value("${autoscaling.borrower.working-directory:../borrower-service}") String workingDirectory,
            @Value("${autoscaling.borrower.jar:target/borrower-service-0.0.1-SNAPSHOT.jar}") String jar,
            @Value("${autoscaling.borrower.log-directory:../runtime/autoscaling}") String logDirectory,
            @Value("${autoscaling.borrower.first-port:8181}") int firstPort,
            @Value("${autoscaling.borrower.last-port:8190}") int lastPort,
            @Value("${autoscaling.borrower.max-instances:5}") int maxInstances,
            @Value("${autoscaling.borrower.baseline-instances:2}") int baselineInstances,
            @Value("${autoscaling.borrower.startup-timeout-seconds:60}") long startupTimeoutSeconds,
            DiscoveryClient discoveryClient) {

        Path managerDirectory = Path.of("").toAbsolutePath().normalize();
        this.borrowerDirectory = managerDirectory.resolve(workingDirectory).normalize();
        this.borrowerJar = this.borrowerDirectory.resolve(jar).normalize();
        this.logDirectory = managerDirectory.resolve(logDirectory).normalize();
        this.firstPort = firstPort;
        this.lastPort = lastPort;
        this.maxInstances = maxInstances;
        this.baselineInstances = baselineInstances;
        this.startupTimeout = Duration.ofSeconds(startupTimeoutSeconds);
        this.discoveryClient = discoveryClient;

        if (firstPort < 1024 || lastPort < firstPort || lastPort > 65535) {
            throw new IllegalArgumentException("Invalid autoscaling.borrower port range.");
        }
        if (maxInstances < 1 || baselineInstances < 1 || baselineInstances > maxInstances || startupTimeoutSeconds < 1) {
            throw new IllegalArgumentException("Borrower max instances and startup timeout must be positive.");
        }
    }


    /**
     * Establishes the configured baseline after the manager has started.
     * The primary Borrower JVM is launched by start-demo.ps1; this manager
     * creates the remaining baseline JVM(s) and waits for Eureka registration.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureBaselineInstances() {
        Instant deadline = Instant.now().plus(startupTimeout);
        while (Instant.now().isBefore(deadline)) {
            boolean primaryRegistered = discoveryClient.getInstances("borrower-service").stream()
                    .anyMatch(instance -> instance.getPort() == 8081);
            if (primaryRegistered) {
                break;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for the primary Borrower instance to register.");
                return;
            }
        }

        int registered = discoveryClient.getInstances("borrower-service").size();
        if (registered == 0) {
            log.error("Primary Borrower instance did not register with Eureka; baseline startup is incomplete.");
            return;
        }

        while (registered < baselineInstances) {
            log.info("Starting baseline Borrower capacity: {}/{} instance(s) registered.",
                    registered, baselineInstances);
            if (!scaleOut()) {
                log.error("Unable to establish the configured Borrower baseline of {} instances.", baselineInstances);
                return;
            }
            registered = discoveryClient.getInstances("borrower-service").size();
        }
        baselineReady = true;
        log.info("Borrower baseline established: {} instance(s) registered with Eureka.", registered);
    }

    public boolean isBaselineReady() {
        return baselineReady;
    }

    /**
     * Starts one secondary JVM when capacity permits. Returns true only after its health check succeeds.
     */
    public synchronized boolean scaleOut() {
        removeExitedProcesses();

        int registeredInstances = discoveryClient.getInstances("borrower-service").size();
        if (registeredInstances >= maxInstances) {
            log.warn("Borrower maximum instance count ({}) has been reached. Eureka currently reports {} instance(s).",
                    maxInstances, registeredInstances);
            return false;
        }
        if (!Files.isRegularFile(borrowerJar)) {
            log.error("Borrower executable JAR not found at {}. Build borrower-service first with Maven package.", borrowerJar);
            return false;
        }

        OptionalInt availablePort = findAvailablePort();
        if (availablePort.isEmpty()) {
            log.error("No free port available in configured range {}-{}.", firstPort, lastPort);
            return false;
        }

        int port = availablePort.getAsInt();
        Path outputFile = logDirectory.resolve("borrower-" + port + ".log");

        try {
            Files.createDirectories(logDirectory);
            ProcessBuilder builder = new ProcessBuilder(
                    javaExecutable(),
                    "-jar",
                    borrowerJar.toString(),
                    "--server.port=" + port,
                    "--borrower.jobs-enabled=false",
                    "--recovery.enabled=false",
                    "--spring.application.name=borrower-service",
                    "--eureka.instance.instance-id=borrower-service:" + port
            );
            builder.directory(borrowerDirectory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());

            Process process = builder.start();
            ManagedProcess managed = new ManagedProcess(process, port, Instant.now(), outputFile);
            instances.add(managed);

            log.info("Started additional Borrower JVM. pid={}, port={}, log={}",
                    process.pid(), port, outputFile);

            if (!waitUntilHealthy(port, process)) {
                instances.remove(managed);
                process.destroy();
                log.error("Borrower JVM pid={} on port {} did not become healthy. See {}",
                        process.pid(), port, outputFile);
                return false;
            }

            if (!waitUntilRegistered(port, process)) {
                instances.remove(managed);
                process.destroy();
                log.error("Borrower JVM pid={} on port {} became healthy but did not register with Eureka. See {}",
                        process.pid(), port, outputFile);
                return false;
            }

            log.info("Additional Borrower instance is healthy and registered with Eureka. pid={}, port={}",
                    process.pid(), port);
            return true;
        } catch (IOException exception) {
            log.error("Failed to start additional Borrower JVM on port {}: {}", port, exception.getMessage(), exception);
            return false;
        }
    }

    public synchronized List<ManagedInstance> getInstances() {
        removeExitedProcesses();
        return instances.stream()
                .map(instance -> new ManagedInstance(
                        instance.process.pid(),
                        instance.port,
                        instance.startedAt,
                        instance.process.isAlive() ? "RUNNING" : "EXITED"))
                .toList();
    }

    private OptionalInt findAvailablePort() {
        for (int port = firstPort; port <= lastPort; port++) {
            int candidatePort = port;
            boolean alreadyTracked = instances.stream().anyMatch(instance -> instance.port == candidatePort);
            if (alreadyTracked) {
                continue;
            }
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress("127.0.0.1", port));
                return OptionalInt.of(port);
            } catch (IOException ignored) {
                // Port is already occupied; try the next one.
            }
        }
        return OptionalInt.empty();
    }

    private boolean waitUntilHealthy(int port, Process process) {
        Instant deadline = Instant.now().plus(startupTimeout);
        URI healthUri = URI.create("http://127.0.0.1:" + port + "/actuator/health");

        while (Instant.now().isBefore(deadline) && process.isAlive()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(healthUri)
                        .timeout(Duration.ofSeconds(3))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"")) {
                    return true;
                }
            } catch (Exception ignored) {
                // The application may still be starting; retry until timeout.
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean waitUntilRegistered(int port, Process process) {
        Instant deadline = Instant.now().plus(startupTimeout);
        while (Instant.now().isBefore(deadline) && process.isAlive()) {
            boolean registered = discoveryClient.getInstances("borrower-service").stream()
                    .anyMatch(instance -> instance.getPort() == port);
            if (registered) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private void removeExitedProcesses() {
        instances.removeIf(instance -> {
            if (instance.process.isAlive()) {
                return false;
            }
            log.warn("Managed Borrower JVM exited. pid={}, port={}, exitCode={}",
                    instance.process.pid(), instance.port, instance.process.exitValue());
            return true;
        });
    }

    private String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private record ManagedProcess(Process process, int port, Instant startedAt, Path logFile) {
    }
}
