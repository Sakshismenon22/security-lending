import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { loadWorkspaceSession, lendoraUrl } from "./api/sessionApi";
import { configureOrganization } from "./api/apiClient";
import { BrowserRouter } from "react-router-dom";
import "./styles/global.css";
import "./styles/purpleTheme.css";
const root = createRoot(document.getElementById("root"));
root.render(
  <p style={{ padding: 32, fontFamily: "sans-serif" }} role="status">
    Opening your workspace…
  </p>,
);
loadWorkspaceSession()
  .then(async (identity) => {
    if (identity === undefined) return;
    if (identity)
      configureOrganization(identity.organization.platformOrganizationId);

    const App = (await import("./App.jsx")).default;
    root.render(
      <StrictMode>
        <BrowserRouter>
          <App identity={identity} />
        </BrowserRouter>
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
