import { defineConfig } from "@playwright/test";
export default defineConfig({
  testDir: "./tests/browser",
  workers: 1,
  use: {
    baseURL: "http://127.0.0.1:5177",
    viewport: { width: 1440, height: 1000 },
  },
  webServer: {
    command: "npm run dev -- --port 5177",
    url: "http://127.0.0.1:5177",
    reuseExistingServer: false,
    timeout: 30000,
  },
});
