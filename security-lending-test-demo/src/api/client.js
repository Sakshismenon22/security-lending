export async function sendRequest({
  side,
  path,
  method = "GET",
  body,
  headers = {},
  signal,
}) {
  const started = performance.now();
  const response = await fetch(`/bridge/${side}${path}`, {
    method,
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
