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
import java.util.regex.Pattern;

/**
 * Starts and tracks additional local Borrower Service JVM processes.
 * Owns secondary Borrower JVMs and safely scales them out or in without stopping
 * the primary Borrower process or reducing capacity below the configured baseline.
 */
@Component
public class ProcessManager {

    private static final Logger log = LoggerFactory.getLogger(ProcessManager.class);
    private static final Pattern UP_HEALTH_STATUS = Pattern.compile("\"status\"\\s*:\\s*\"UP\"");

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
                    "-Xms128m",
                    "-Xmx512m",
                    "-XX:MaxMetaspaceSize=192m",
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

    /**
     * Stops the newest manager-owned secondary JVM, never the primary Borrower process.
     * The configured baseline is preserved; returns false when no safe scale-in is possible.
     */
    public synchronized boolean scaleIn() {
        removeExitedProcesses();

        int registeredInstances = discoveryClient.getInstances("borrower-service").size();
        if (registeredInstances <= baselineInstances || instances.isEmpty()) {
            log.info("Scale-in skipped: registered instances={}, baseline={}, managed secondary JVMs={}.",
                    registeredInstances, baselineInstances, instances.size());
            return false;
        }

        ManagedProcess candidate = instances.stream()
                .filter(instance -> instance.process.isAlive())
                .max(java.util.Comparator.comparing(ManagedProcess::startedAt))
                .orElse(null);

        if (candidate == null) {
            log.warn("Scale-in skipped: no live manager-owned secondary Borrower JVM is available.");
            return false;
        }

        log.info("Scaling in Borrower Service by stopping managed JVM pid={} on port {}.",
                candidate.process.pid(), candidate.port);
        candidate.process.destroy();

        try {
            if (!candidate.process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
                log.warn("Borrower JVM pid={} on port {} did not stop gracefully; forcing termination.",
                        candidate.process.pid(), candidate.port);
                candidate.process.destroyForcibly();
                if (!candidate.process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.error("Unable to terminate Borrower JVM pid={} on port {}.",
                            candidate.process.pid(), candidate.port);
                    return false;
                }
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while stopping Borrower JVM pid={} on port {}.",
                    candidate.process.pid(), candidate.port);
            return false;
        }

        instances.remove(candidate);
        log.info("Scale-in completed. Stopped Borrower JVM pid={} on port {}. Registered instances before deregistration settles={}.",
                candidate.process.pid(), candidate.port, registeredInstances);
        return true;
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
        int lastStatusCode = -1;
        String lastResponseBody = "No HTTP response received yet.";
        String lastError = null;

        while (Instant.now().isBefore(deadline) && process.isAlive()) {
            try {
                HttpRequest request = HttpRequest.newBuilder(healthUri)
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                lastStatusCode = response.statusCode();
                lastResponseBody = response.body() == null ? "" : response.body();
                if (lastStatusCode == 200 && UP_HEALTH_STATUS.matcher(lastResponseBody).find()) {
                    log.info("Borrower instance on port {} passed its Actuator health check.", port);
                    return true;
                }
            } catch (Exception exception) {
                lastError = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }

            try {
                Thread.sleep(1500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for Borrower health on port {}.", port);
                return false;
            }
        }

        if (!process.isAlive()) {
            log.error("Borrower process on port {} exited before becoming healthy. exitCode={}",
                    port, process.exitValue());
        } else {
            log.error("Borrower health check timed out on port {} after {} seconds. Last HTTP status={}, body={}, last error={}",
                    port, startupTimeout.toSeconds(), lastStatusCode,
                    abbreviate(lastResponseBody, 500), lastError);
        }
        return false;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
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
