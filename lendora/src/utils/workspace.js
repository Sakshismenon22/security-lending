export function workspaceUrl(type) {
  if (type === "LENDER")
    return import.meta.env.VITE_LENDDESK_URL || "http://localhost:5175";
  if (type === "BORROWER")
    return import.meta.env.VITE_BORROWDESK_URL || "http://localhost:5173";
  return null;
}
