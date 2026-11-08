import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

export const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "..",
);

export function createConfig(env = process.env) {
  const parent = path.resolve(projectRoot, "..");
  const candidates = fs
    .readdirSync(parent)
    .filter((name) => /^kafka(?:_[\d.]+)?-[\d.]+$/.test(name))
    .sort()
    .reverse();
  const kafkaHome = path.resolve(
    env.KAFKA_HOME || path.join(parent, candidates[0] || "kafka_2.13-4.4.0"),
  );
  const runtime = path.resolve(
    env.KAFKA_UI_RUNTIME || path.join(parent, "runtime"),
  );
  const brokers = (env.KAFKA_BOOTSTRAP_SERVER || "localhost:9092")
    .split(",")
    .map((value) => value.trim());
  const match = /^(localhost|127\.0\.0\.1):(\d+)$/.exec(brokers[0]);
  const brokerPort = match ? Number(match[2]) : 9092;
  const controllerPort = Number(env.KAFKA_CONTROLLER_PORT || brokerPort + 1);
  const controlPort = Number(env.KAFKA_CONTROL_PORT || brokerPort + 2);
  for (const port of [brokerPort, controllerPort, controlPort]) {
    if (!Number.isInteger(port) || port < 1024 || port > 65535)
      throw new Error("Kafka ports must be integers between 1024 and 65535.");
  }
  if (new Set([brokerPort, controllerPort, controlPort]).size !== 3)
    throw new Error("Broker, controller, and control ports must differ.");
  return {
    projectRoot,
    kafkaHome,
    runtime,
    brokers,
    brokerPort,
    controllerPort,
    controlPort,
    local: brokers.length === 1 && Boolean(match),
    port: Number(env.PORT || 3001),
    host: env.HOST || "127.0.0.1",
    java:
      env.JAVA_BIN ||
      (env.JAVA_HOME
        ? path.join(
            env.JAVA_HOME,
            "bin",
            process.platform === "win32" ? "java.exe" : "java",
          )
        : "java"),
    heapMb: Math.min(4096, Math.max(256, Number(env.KAFKA_HEAP_MB) || 512)),
    version: path.basename(kafkaHome).match(/-([\d.]+)$/)?.[1] || "Bundled",
  };
}

export const lendingTopics = [
  "borrower.commands.v1",
  "borrower.commands.v1.DLT",
  "borrower.events.v1",
  "borrower.confirmations.v1",
  "borrower.confirmations.v1.DLT",
  "lender.commands.v1",
  "lender.commands.v1.DLT",
  "lender.confirmations.v1",
  "lender.confirmations.v1.DLT",
  "lender.events.v1",
  "lender.lifecycle.commands.v1",
  "lender.lifecycle.commands.v1.DLT",
  "lender.lifecycle.events.v1",
  "lender.lifecycle.events.v1.DLT",
  "market.prices.v1",
  "market.prices.v1.DLT",
];
