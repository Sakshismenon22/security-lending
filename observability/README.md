# Run Lendora with metrics on Windows

Set `GRAFANA_PATH` and `PROMETHEUS_PATH` to your installed executables in the repository-root `.env`. Quote paths with spaces. Start PostgreSQL and Redis as required by your application settings, then close any running backend windows so the launcher can start them with the metrics profile.

```cmd
start-lendora.cmd -Observability
```

The launcher also accepts `-Observabilty`. Use `-Plan` with either spelling to list what will start.

The launcher runs Prometheus on port 9090 using [prometheus.yaml](prometheus.yaml) and Grafana on port 3000 using your installed executable. Existing instances on those ports can be reused when they are ready and Prometheus has the lending scrape job. The market-data service keeps the Redis profile selected in its application settings.

In Grafana at `http://localhost:3000`, set the Prometheus data source URL to `http://127.0.0.1:9090` and import [Security Lending Operations](grafana/lending-operations.json). Check `http://localhost:9090/targets` to see the service scrape status. Kafka panels require a Kafka exporter on port 9308.

Use `start-lendora.cmd` for the regular startup.
