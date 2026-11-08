import test from "node:test";
import assert from "node:assert/strict";
import { actions, defaults, requestPath } from "../src/api/catalog.js";
import { rowsOf, sendRequest } from "../src/api/client.js";
import { createProxy } from "../config/proxy.js";
test("FIX proxy routes cannot be shadowed by business-service prefixes", () => {
  const proxy = createProxy();
  for (const [side, port] of [
    ["borrower", 8081],
    ["borrowerAdapter", 8082],
    ["lender", 8084],
    ["lenderAdapter", 8083],
  ]) {
    const path = `/bridge/${side}/api/v1/adapter/status`;
    const match = Object.entries(proxy).find(([prefix]) =>
      path.startsWith(prefix),
    );
    assert.equal(match[1].target, `http://127.0.0.1:${port}`);
    assert.equal(match[1].rewrite(path), "/api/v1/adapter/status");
  }
  let removed;
  proxy["/bridge/borrower/"].configure({
    on: (_event, fn) =>
      fn({
        removeHeader: (key) => {
          removed = key;
        },
      }),
  });
  assert.equal(removed, "origin");
});
test("all action IDs are unique and all paths are catalogued", () => {
  assert.equal(new Set(actions.map((a) => a.id)).size, actions.length);
  assert.ok(actions.length > 110);
  for (const a of actions) {
    assert.ok(a.path.startsWith("/"));
    assert.ok(["GET", "POST", "PUT", "PATCH", "DELETE"].includes(a.method));
  }
});
test("borrower and lender IDs remain local and invalid IDs are refused", () => {
  const a = actions.find((a) => a.id === "loan-settle");
  assert.equal(
    requestPath(a, 42, 17),
    "/api/v1/organizations/42/loans/17/settle",
  );
  assert.throws(() => requestPath(a, "", "1"));
  assert.throws(() => requestPath(a, 42, "../../other"));
  assert.equal(
    requestPath(
      actions.find((a) => a.id === "register-l"),
      "",
      "",
    ),
    "/api/v1/organizations",
  );
});
test("request defaults preserve financial decimals, null security IDs, and unique keys", () => {
  const a = actions.find((a) => a.id === "agreement-propose");
  const d = defaults(a, new Date("2026-11-09T12:00:00Z"));
  assert.equal(d.coverageRatio, 1.05);
  assert.equal(d.feeRate, 0.03);
  assert.equal(d.collateralRules[0].securityId, null);
  assert.equal(d.effectiveDate, "2026-11-09");
  assert.notEqual(d.number, defaults(a).number);
  assert.equal(
    defaults(actions.find((a) => a.id === "loan")).collateral[0].assetType,
    "CASH",
  );
});
test("read pagination and API envelopes are handled", () => {
  const a = actions.find(
    (a) => a.path === "/loans" && a.side === "borrower" && a.method === "GET",
  );
  assert.equal(
    requestPath(a, 5, "", 2, 50),
    "/api/v1/organizations/5/loans?page=2&size=50",
  );
  assert.deepEqual(
    rowsOf({ success: true, data: { content: [{ id: 3 }], totalElements: 1 } }),
    [{ id: 3 }],
  );
  assert.deepEqual(rowsOf({ data: [{ id: 4 }] }), [{ id: 4 }]);
  assert.deepEqual(rowsOf({ data: { id: 4 } }), []);
});
test("HTTP errors remain failures and fee idempotency keys reach the proxy", async () => {
  const original = globalThis.fetch;
  let observed;
  globalThis.fetch = async (url, options) => {
    observed = { url, options };
    return new Response(
      JSON.stringify({ title: "Conflict", detail: "Loan not active" }),
      { status: 409 },
    );
  };
  try {
    const r = await sendRequest({
      side: "borrower",
      path: "/api/v1/organizations/5/fees/7/payments",
      method: "POST",
      headers: { "Idempotency-Key": "retry-same-key" },
    });
    assert.equal(r.ok, false);
    assert.equal(r.status, 409);
    assert.equal(observed.options.headers["Idempotency-Key"], "retry-same-key");
    assert.equal(
      observed.url,
      "/bridge/borrower/api/v1/organizations/5/fees/7/payments",
    );
  } finally {
    globalThis.fetch = original;
  }
});
