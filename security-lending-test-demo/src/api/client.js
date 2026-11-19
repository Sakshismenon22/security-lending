export async function sendRequest({
  side,
  path,
  method = "GET",
  body,
  headers = {},
  signal,
}) {
  const started = performance.now();
  const targetSide = path.startsWith("/api/v1/lifecycle") ? "lifecycle" : side;
  const response = await fetch(`/bridge/${targetSide}${path}`, {
    method,
    credentials: "omit",
    headers: {
      Accept: "application/json",
      ...(body !== undefined ? { "Content-Type": "application/json" } : {}),
      ...headers,
    },
    ...(body !== undefined ? { body: JSON.stringify(body) } : {}),
    signal: signal || AbortSignal.timeout(25000),
  });
  const text = await response.text();
  let payload;
  try {
    payload = JSON.parse(text);
  } catch {
    payload = text;
  }
  return {
    ok: response.ok && payload?.success !== false,
    status: response.status,
    payload,
    duration: Math.round(performance.now() - started),
    timestamp: new Date().toISOString(),
    side,
    path,
    method,
  };
}
export const unwrap = (payload) => payload?.data ?? payload;
export const rowsOf = (payload) => {
  const data = unwrap(payload);
  return Array.isArray(data)
    ? data
    : Array.isArray(data?.content)
      ? data.content
      : [];
};

export function requestError(result, fallback = "Request failed") {
  const detail =
    typeof result.payload?.detail === "string"
      ? result.payload.detail
      : typeof result.payload?.message === "string"
        ? result.payload.message
        : "";
  if (
    ["borrower", "lender"].includes(result.side) &&
    [401, 403].includes(result.status)
  ) {
    return `${result.side === "borrower" ? "Borrower" : "Lender"} API returned ${result.status}. Lendora authentication is enabled or access is restricted. This test console does not log in as its selected organization/user. For local cross-platform testing, stop the existing borrower and lender backend processes and run start-demo.cmd (without -Lendora). Use Lendora/BorrowDesk/LendDesk for authenticated access.${detail ? " Service response: " + detail : ""}`;
  }
  return `${fallback} (HTTP ${result.status})${detail ? ": " + detail : ""}`;
}
