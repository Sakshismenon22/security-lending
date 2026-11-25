import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { loadWorkspaceSession, lendoraUrl } from "./api/sessionApi";
import "./index.css";

// Use the same loopback host as the gateway and the shared session cookie.
const pageUrl = new URL(window.location.href);
const accountUrl = new URL(
  import.meta.env.VITE_LENDORA_API_URL || "http://localhost:8090",
  pageUrl,
);
if (pageUrl.hostname === "127.0.0.1" && accountUrl.hostname === "localhost") {
  pageUrl.hostname = "localhost";
  window.location.replace(pageUrl.href);
} else {
  const root = createRoot(document.getElementById("root"));
  root.render(
    <p style={{ padding: 32, fontFamily: "sans-serif" }} role="status">
      Opening your workspace…
    </p>,
  );
  loadWorkspaceSession()
    .then(async (identity) => {
      if (identity === undefined) return;

      const App = (await import("./App.jsx")).default;
      root.render(
        <StrictMode>
          <App identity={identity} />
        </StrictMode>,
      );
    })
    .catch((error) =>
      root.render(
        <main style={{ padding: 32, fontFamily: "sans-serif" }}>
          <h1>Workspace unavailable</h1>
          <p role="alert">{error.message}</p>
          <button onClick={() => window.location.reload()}>Try again</button>{" "}
          <a href={lendoraUrl + "/login"}>Back to log in</a>
        </main>,
      ),
    );
}
