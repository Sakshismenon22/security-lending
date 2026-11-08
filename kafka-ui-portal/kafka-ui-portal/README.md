# Relay — portable Kafka workspace

A React console and Express API for the bundled Apache Kafka broker. Includes cluster status, topic search/create/delete, partition offsets, keyed JSON or text publishing, bounded message inspection, consumer groups, and broker logs. All displayed metrics come from Kafka.

## Start on Windows

Install Node.js 22+ and a JDK 17+ (JDK 21 recommended). Keep the `kafka_2.13-4.4.0` distribution beside this app folder. Double-click `../start-portal.cmd`, then open http://127.0.0.1:3001.

Or run from this folder:

```powershell
npm ci
npm run build
npm start
```

If Java is not on PATH, set `JAVA_HOME` or `JAVA_BIN` before starting. No Git Bash, Docker, ZooKeeper, or Windows service is required.

Click **Initialize & start** on first use. The portal formats only its dedicated `../runtime/data` directory, then starts a combined KRaft broker/controller. Subsequent starts preserve topics. Settings provides graceful shutdown; closing the portal does not stop Kafka. Restarting the portal reconnects to its managed process.

For development, `npm run dev` starts the API on 3001 and Vite on http://127.0.0.1:5175 (separate from the borrower frontend).

## Connect services

Default bootstrap server: `localhost:9092`. Use **Set up lending topics** to create the project's borrower, lender, lifecycle, market-price and `.DLT` topics. Existing topics are preserved. Then point the Spring services at that bootstrap address.

## Configuration

Environment variables are optional; set them before launching Node (no automatic .env loading).

| Variable                 | Default                                    |
| ------------------------ | ------------------------------------------ |
| `KAFKA_HOME`             | Auto-discovered sibling Kafka distribution |
| `KAFKA_UI_RUNTIME`       | `../runtime` relative to this app          |
| `KAFKA_BOOTSTRAP_SERVER` | `localhost:9092`                           |
| `KAFKA_CONTROLLER_PORT`  | Broker port + 1                            |
| `KAFKA_CONTROL_PORT`     | Broker port + 2                            |
| `JAVA_BIN`               | `JAVA_HOME/bin/java` or Java on PATH       |
| `KAFKA_HEAP_MB`          | 512                                        |
| `PORT`                   | 3001                                       |

The whole parent folder is portable between machines with these prerequisites. Stop the managed broker before moving it. The generated server configuration is rebuilt with the current runtime path on each start. Do not copy an active broker's data directory.

## Behavior and limits

- One local broker with replication factor 1, seven-day retention, and explicit topic creation.
- The local console binds to loopback and has no authentication. It is intended for development; do not expose it publicly.
- An already running external broker can be inspected and used for topic/message operations, but cannot be stopped through this portal.
- A token-authenticated loopback Java shutdown channel stops only the JVM launched by this portal and runs Kafka's normal shutdown hooks. No unrelated Java process is killed.
- Message reads use a temporary consumer group without committing application offsets. Snapshots are limited to 100 records with an eight-second fetch window after joining. Latest reads start at high offset minus the limit in each partition; compacted logs can contain gaps. Offset span is not an exact record count. Consumer-group lag and historical throughput are not calculated.
- Broker logs are the last 200 lines from the managed broker, not from external Kafka processes.
- Original Kafka configuration and existing bundled storage are never formatted by the portal.

## Verification

```powershell
npm test
npx playwright install chromium
npm run build
npm run test:e2e
```

The browser integration test starts a separate broker on ports 29092–29094, using `test-results/runtime`. It checks lifecycle controls, topic creation, keyed multiline message publication/readback, validation, lending-topic setup, search, logs and mobile layout. The test shuts down its broker in cleanup. Desktop/mobile screenshots are written to `test-results`.
