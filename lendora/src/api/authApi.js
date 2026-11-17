const base = (import.meta.env.VITE_AUTH_API_URL || "/api/v1").replace(
  /\/$/,
  "",
);
async function post(path, body, signal) {
  let response;
  try {
    response = await fetch(`${base}${path}`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error(
      "We could not connect. Check your connection and try again.",
      { cause: error },
    );
  }
  const payload = await response.json().catch(() => null);
  if (!response.ok || !payload || payload.success !== true) {
    if (
      [502, 504].includes(response.status) ||
      (!payload && response.status >= 500)
    )
      throw new Error(
        "Cannot reach the Lendora account service. Start lendora-service on port 8086 and check the frontend proxy target.",
      );
    if (
      [404, 405, 501].includes(response.status) ||
      !payload ||
      (response.status === 503 && !payload.message && !payload.detail)
    )
      throw new Error(
        "This service is currently unavailable. Please try again later.",
      );
    throw new Error(
      typeof payload.message === "string"
        ? payload.message
        : typeof payload.detail === "string"
          ? payload.detail
          : "We could not complete your request. Please check your details and try again.",
    );
  }
  return payload.data ?? payload;
}
export const authApi = {
  me: async (signal) => {
    const response = await fetch(`${base}/auth/me`, {
      credentials: "include",
      headers: { Accept: "application/json" },
      signal,
    });
    const payload = await response.json().catch(() => null);
    if (!response.ok || !payload?.success)
      throw new Error(
        response.status === 401
          ? "Please log in to continue."
          : "Account service is unavailable.",
      );
    return payload.data;
  },
  logout: (signal) => post("/auth/logout", {}, signal),
  login: (values, signal) => post("/auth/login", values, signal),
  register: (values, signal) => post("/organizations/register", values, signal),
  resetPassword: (values, signal) =>
    post("/auth/password-reset", values, signal),
};
