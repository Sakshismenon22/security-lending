import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { loadWorkspaceSession, lendoraUrl } from "./api/sessionApi";
import "./index.css";
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
