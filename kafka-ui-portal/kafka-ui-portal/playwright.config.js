import { defineConfig } from "@playwright/test";
import path from "node:path";
export default defineConfig({
  testDir: "./tests/browser",
  workers: 1,
  timeout: 120000,
  use: {
    baseURL: "http://127.0.0.1:3101",
    viewport: { width: 1440, height: 1050 },
  },
  webServer: {
    command: "node server/index.js",
    url: "http://127.0.0.1:3101",
    reuseExistingServer: false,
    timeout: 30000,
    env: {
      PORT: "3101",
      KAFKA_BOOTSTRAP_SERVER: "localhost:29092",
      KAFKA_CONTROLLER_PORT: "29093",
      KAFKA_CONTROL_PORT: "29094",
      KAFKA_UI_RUNTIME: path.resolve("test-results/runtime"),
      JAVA_BIN: process.env.JAVA_BIN || "java",
    },
  },
});
