const base = (
  import.meta.env.VITE_LENDORA_API_URL || "http://localhost:8086/api/v1"
).replace(/\/$/, "");
export const lendoraUrl = (
  import.meta.env.VITE_LENDORA_URL || "http://localhost:5176"
).replace(/\/$/, "");
let identity = null;
export const getWorkspaceIdentity = () => identity;
export async function loadWorkspaceSession() {
  if (import.meta.env.VITE_LENDORA_AUTH_REQUIRED === "false") return null;
  const response = await fetch(base + "/auth/me", {
    credentials: "include",
    headers: { Accept: "application/json" },
  });
  if (response.status === 401) {
    window.location.replace(lendoraUrl + "/login");
    return undefined;
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload?.success)
    throw new Error("Account service is unavailable. Please try again.");
  identity = payload.data;
  if (!["LENDER", "BORROWER"].includes(identity?.organization?.type))
    throw new Error("Your account session is invalid. Please log in again.");
  if (identity.organization.type !== "LENDER") {
    const target =
      import.meta.env.VITE_BORROWDESK_URL || "http://localhost:5173";
    window.location.replace(target);
    return undefined;
  }
  if (
    !Number.isSafeInteger(identity.organization.platformOrganizationId) ||
    identity.organization.platformOrganizationId <= 0
  )
    throw new Error("Your organization workspace is not ready.");
  return identity;
}
export async function logoutWorkspace() {
  const response = await fetch(base + "/auth/logout", {
    method: "POST",
    credentials: "include",
    headers: { "Content-Type": "application/json" },
    body: "{}",
  });
  if (!response.ok) throw new Error("Could not log out. Please try again.");
  window.location.replace(lendoraUrl + "/login");
}
