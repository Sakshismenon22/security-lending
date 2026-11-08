export function createProxy(env = {}) {
  const ports = {
    borrower: 8081,
    lender: 8084,
    borrowerAdapter: 8082,
    lenderAdapter: 8083,
    market: 8080,
  };
  return Object.fromEntries(
    Object.entries(ports).map(([side, port]) => [
      `/bridge/${side}/`,
      {
        target:
          env[`DEMO_${side.toUpperCase()}_URL`] || `http://127.0.0.1:${port}`,
        changeOrigin: true,
        rewrite: (path) => path.slice(`/bridge/${side}`.length),
        timeout: 30000,
        proxyTimeout: 30000,
        configure: (proxy) =>
          proxy.on("proxyReq", (req) => req.removeHeader("origin")),
      },
    ]),
  );
}
