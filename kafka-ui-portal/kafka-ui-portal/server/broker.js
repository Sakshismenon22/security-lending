import fs from "node:fs";
import path from "node:path";
import net from "node:net";
import { spawn } from "node:child_process";
import { randomBytes } from "node:crypto";
import { pathToFileURL } from "node:url";
import { HttpError } from "./errors.js";

const pause = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
export const portOpen = (port, host = "127.0.0.1") =>
  new Promise((resolve) => {
    const socket = net.createConnection({ port, host });
    const finish = (result) => {
      socket.destroy();
      resolve(result);
    };
    socket.setTimeout(600);
    socket.once("connect", () => finish(true));
    socket.once("timeout", () => finish(false));
    socket.once("error", () => finish(false));
  });

export class BrokerManager {
  constructor(config) {
    this.config = config;
    this.stateFile = path.join(config.runtime, "process.json");
    this.configFile = path.join(config.runtime, "server.properties");
    this.logFile = path.join(config.runtime, "broker.log");
    this.pending = null;
    this.startedAt = null;
    this.lastError = null;
  }
  get initialized() {
    return fs.existsSync(
      path.join(this.config.runtime, "data", "meta.properties"),
    );
  }
  get available() {
    return (
      fs.existsSync(path.join(this.config.kafkaHome, "libs")) &&
      fs.existsSync(path.join(this.config.kafkaHome, "bin", "kafka-storage.sh"))
    );
  }
  javaArgs() {
    return [
      `-Xms128m`,
      `-Xmx${this.config.heapMb}m`,
      `-Dlog4j2.configurationFile=${pathToFileURL(path.join(this.config.kafkaHome, "config", "log4j2.yaml")).href}`,
      `-Dkafka.logs.dir=${path.join(this.config.runtime, "logs")}`,
      "-cp",
      path.join(this.config.kafkaHome, "libs", "*"),
    ];
  }
  runJava(args, timeout = 45000) {
    return new Promise((resolve, reject) => {
      const child = spawn(this.config.java, [...this.javaArgs(), ...args], {
        windowsHide: true,
        shell: false,
      });
      let output = "";
      let done = false;
      const finish = (error) => {
        if (done) return;
        done = true;
        clearTimeout(timer);
        error ? reject(error) : resolve(output);
      };
      const timer = setTimeout(() => {
        child.kill();
        finish(
          new HttpError(
            504,
            "Kafka storage initialization timed out. Check broker logs.",
          ),
        );
      }, timeout);
      child.stdout.on("data", (data) => {
        output = (output + data).slice(-24000);
      });
      child.stderr.on("data", (data) => {
        output = (output + data).slice(-24000);
      });
      child.on("error", (error) =>
        finish(new HttpError(503, `Java could not start: ${error.message}`)),
      );
      child.on("exit", (code) =>
        finish(
          code === 0
            ? null
            : new HttpError(500, output || `Java exited with code ${code}.`),
        ),
      );
    });
  }
  writeConfig() {
    fs.mkdirSync(this.config.runtime, { recursive: true });
    fs.mkdirSync(path.join(this.config.runtime, "logs"), { recursive: true });
    const data = path.join(this.config.runtime, "data").replaceAll("\\", "/");
    // Dedicated managed configuration: never edit or format the bundled distribution's existing storage.
    fs.writeFileSync(
      this.configFile,
      [
        "process.roles=broker,controller",
        "node.id=1",
        `controller.quorum.bootstrap.servers=127.0.0.1:${this.config.controllerPort}`,
        `listeners=PLAINTEXT://127.0.0.1:${this.config.brokerPort},CONTROLLER://127.0.0.1:${this.config.controllerPort}`,
        `advertised.listeners=PLAINTEXT://localhost:${this.config.brokerPort}`,
        "controller.listener.names=CONTROLLER",
        "inter.broker.listener.name=PLAINTEXT",
        "listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT",
        `log.dirs=${data}`,
        "num.network.threads=3",
        "num.io.threads=4",
        "num.partitions=1",
        "offsets.topic.replication.factor=1",
        "transaction.state.log.replication.factor=1",
        "transaction.state.log.min.isr=1",
        "share.coordinator.state.topic.replication.factor=1",
        "share.coordinator.state.topic.min.isr=1",
        "group.initial.rebalance.delay.ms=0",
        "auto.create.topics.enable=false",
        "delete.topic.enable=true",
        "log.retention.hours=168",
        "log.segment.bytes=1073741824",
        "log.cleaner.dedupe.buffer.size=16777216",
        "",
      ].join("\n"),
    );
  }
  state() {
    try {
      return JSON.parse(fs.readFileSync(this.stateFile, "utf8"));
    } catch {
      return null;
    }
  }
  async owned() {
    const state = this.state();
    if (!state || state.controlPort !== this.config.controlPort) return null;
    try {
      const response = await fetch(
        `http://127.0.0.1:${state.controlPort}/health`,
        {
          headers: { Authorization: `Bearer ${state.token}` },
          signal: AbortSignal.timeout(800),
        },
      );
      if (response.ok && (await response.json()).pid === state.pid)
        return state;
    } catch {
      /* The process might still be compiling its Java source launcher. */
    }
    return null;
  }
  async initialize() {
    if (this.pending)
      throw new HttpError(409, "A broker operation is already in progress.");
    if (!this.config.local)
      throw new HttpError(
        409,
        "Storage can only be initialized for the configured local broker.",
      );
    if (!this.available)
      throw new HttpError(
        503,
        "Kafka distribution was not found. Set KAFKA_HOME or keep it beside the portal folder.",
      );
    if (this.initialized)
      return {
        message: "Storage is already initialized. Existing data was preserved.",
      };
    this.pending = "initializing";
    try {
      if (await portOpen(this.config.brokerPort))
        throw new HttpError(
          409,
          "The broker port is already in use. Existing brokers and data will not be replaced.",
        );
      this.writeConfig();
      await this.runJava([
        "kafka.tools.StorageTool",
        "format",
        "--standalone",
        "--cluster-id",
        randomBytes(16).toString("base64url"),
        "--config",
        this.configFile,
      ]);
      return { message: "Local Kafka storage initialized." };
    } finally {
      this.pending = null;
    }
  }
  async start() {
    if (this.pending)
      throw new HttpError(409, "A broker operation is already in progress.");
    if (!this.initialized)
      throw new HttpError(
        409,
        "Initialize local storage before starting Kafka.",
      );
    if (!this.config.local)
      throw new HttpError(
        409,
        "This portal only starts its managed local broker.",
      );
    if (this.startedAt && Date.now() - this.startedAt < 60000)
      throw new HttpError(409, "Kafka is already starting.");
    this.pending = "starting";
    try {
      if ((await portOpen(this.config.brokerPort)) || (await this.owned()))
        throw new HttpError(
          409,
          "The broker is already running or its port is in use.",
        );
      if (
        (await portOpen(this.config.controllerPort)) ||
        (await portOpen(this.config.controlPort))
      )
        throw new HttpError(
          409,
          "A controller or management port is already in use.",
        );
      this.writeConfig();
      this.lastError = null;
      const token = randomBytes(32).toString("hex");
      const log = fs.openSync(this.logFile, "a");
      const args = [
        ...this.javaArgs(),
        "--add-modules",
        "jdk.httpserver",
        path.join(
          this.config.projectRoot,
          "server",
          "java",
          "PortalBroker.java",
        ),
        this.configFile,
        String(this.config.controlPort),
        token,
      ];
      const child = spawn(this.config.java, args, {
        cwd: this.config.projectRoot,
        windowsHide: true,
        shell: false,
        detached: true,
        stdio: ["ignore", log, log],
      });
      fs.closeSync(log);
      await new Promise((resolve, reject) => {
        child.once("spawn", resolve);
        child.once("error", reject);
      });
      fs.writeFileSync(
        this.stateFile,
        JSON.stringify({
          pid: child.pid,
          token,
          controlPort: this.config.controlPort,
          startedAt: new Date().toISOString(),
        }),
        { mode: 0o600 },
      );
      this.startedAt = Date.now();
      child.once("exit", (code) => {
        if (code)
          this.lastError = `Kafka exited with code ${code}. See broker logs.`;
        this.startedAt = null;
      });
      child.unref();
      return {
        message: "Kafka is starting. Readiness will be checked automatically.",
        status: "starting",
      };
    } finally {
      this.pending = null;
    }
  }
  async stop() {
    if (this.pending)
      throw new HttpError(409, "A broker operation is already in progress.");
    this.pending = "stopping";
    try {
      const state = await this.owned();
      if (!state)
        throw new HttpError(
          409,
          "This broker was not started by this portal. An external Java process will not be stopped.",
        );
      await fetch(`http://127.0.0.1:${state.controlPort}/shutdown`, {
        method: "POST",
        headers: { Authorization: `Bearer ${state.token}` },
        signal: AbortSignal.timeout(3000),
      });
      const until = Date.now() + 30000;
      while (Date.now() < until) {
        if (
          !(await portOpen(this.config.controlPort)) &&
          !(await portOpen(this.config.brokerPort))
        ) {
          this.startedAt = null;
          return {
            message: "Kafka stopped gracefully. Topic data is preserved.",
          };
        }
        await pause(250);
      }
      throw new HttpError(
        504,
        "Kafka is still shutting down. No force kill was attempted. Check logs and refresh.",
      );
    } finally {
      this.pending = null;
    }
  }
  logs() {
    if (!fs.existsSync(this.logFile)) return [];
    const fd = fs.openSync(this.logFile, "r");
    try {
      const size = fs.fstatSync(fd).size;
      const buffer = Buffer.alloc(Math.min(size, 96000));
      fs.readSync(
        fd,
        buffer,
        0,
        buffer.length,
        Math.max(0, size - buffer.length),
      );
      return buffer.toString("utf8").split(/\r?\n/).filter(Boolean).slice(-200);
    } finally {
      fs.closeSync(fd);
    }
  }
}
