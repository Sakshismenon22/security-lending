# Borrower CPU Autoscaling Test

This guide validates the local JVM scale-out flow. It does not change Kafka topics,
producers, consumers, serializers, or listener configuration.

## Preconditions

1. Start the complete local demo with `.\start-lendora.cmd`.
2. Confirm Eureka shows Borrower instances on ports 8081 and 8181 as `UP`.
3. Confirm the Autoscaling Manager reports that the baseline is ready.
4. Confirm the configured CPU threshold, evaluation interval, consecutive breach
   count, cooldown, and maximum instance count in
   `autoscaling-manager/src/main/resources/application.properties`.

The local-only endpoint is enabled by
`autoscaling.test.cpu-load.enabled=true` in the Borrower Service's local
`application.properties`. The controller is conditional on that property and
rejects requests that do not originate from a loopback address. It allows at
most 4 workers and 90 seconds per request.

## Generate CPU load

Open two PowerShell terminals. Run one command in each terminal so both Borrower
JVMs are stressed at the same time:

Terminal 1:

```powershell
Invoke-RestMethod "http://localhost:8081/internal/autoscaling-test/cpu-load?durationSeconds=75&workers=4"
```

Terminal 2:

```powershell
Invoke-RestMethod "http://localhost:8181/internal/autoscaling-test/cpu-load?durationSeconds=75&workers=4"
```

The endpoint responds after the requested duration with the instance port,
worker count, and number of completed SHA-256 iterations. Keep the load test
limited to a local development environment; it deliberately consumes CPU.

## Observe scale-out

While the load is running, watch these endpoints:

- `http://localhost:8081/actuator/metrics/process.cpu.usage`
- `http://localhost:8181/actuator/metrics/process.cpu.usage`
- `http://localhost:8090/api/autoscaling/borrower/instances`

Also watch the Autoscaling Manager console. With the default 15-second
evaluation interval and 3 consecutive threshold breaches, CPU must remain above
the configured threshold for at least three evaluations before scale-out is
requested. The collector uses the highest CPU reading among registered
Borrower JVMs; the metric is a per-process fraction of one CPU core (0.80 = 80%
of one core).

Expected result: ProcessManager starts another Borrower JVM (normally port
8182), waits for its health check and Eureka registration, and the instances
endpoint/Eureka dashboard then shows three Borrower instances.

## Important

- This is a controlled test endpoint, not a production workload API.
- Do not expose the endpoint to non-loopback clients.
- A CPU load test does not guarantee scale-out if the measured process CPU
  never crosses the configured threshold for the required number of checks.
- Stop the load requests if the development machine becomes unresponsive.
- No Kafka configuration or event-processing code is changed by this test.
