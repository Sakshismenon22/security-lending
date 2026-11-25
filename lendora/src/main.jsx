import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles/global.css";

// Use the same loopback host as the gateway and the shared session cookie.
const pageUrl = new URL(window.location.href);
const accountUrl = new URL(
  import.meta.env.VITE_AUTH_API_URL || "http://localhost:8090",
  pageUrl,
);
if (pageUrl.hostname === "127.0.0.1" && accountUrl.hostname === "localhost") {
  pageUrl.hostname = "localhost";
  window.location.replace(pageUrl.href);
} else {
  createRoot(document.getElementById("root")).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
}
