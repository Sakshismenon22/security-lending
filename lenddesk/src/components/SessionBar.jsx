import { useState } from "react";
import { getWorkspaceIdentity, logoutWorkspace } from "../api/sessionApi";
export default function SessionBar() {
  const identity = getWorkspaceIdentity(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  if (!identity) return null;
  async function logout() {
    setBusy(true);
    setError("");
    try {
      await logoutWorkspace();
    } catch (e) {
      setError(e.message);
      setBusy(false);
    }
  }
  return (
    <div className="workspace-session-bar">
      <span>
        {identity.organization.name} · {identity.user.fullName}
      </span>
      {error && <span role="alert">{error}</span>}
      <button disabled={busy} onClick={logout}>
        {busy ? "Signing out…" : "Log out"}
      </button>
    </div>
  );
}
