import react from "@vitejs/plugin-react";
import process from "node:process";
import { defineConfig, loadEnv } from "vite";
import { createProxy } from "./config/proxy.js";

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const proxy = createProxy(env);
  return {
    plugins: [react()],
    server: { host: "127.0.0.1", port: 5176, strictPort: true, proxy },
    preview: { host: "127.0.0.1", port: 4176, proxy },
  };
});
