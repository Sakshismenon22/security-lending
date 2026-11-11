import { useState } from "react";
import {
  actions,
  categories,
  defaults,
  requestPath,
  workflow,
} from "./api/catalog";
import { sendRequest, unwrap, rowsOf } from "./api/client";
const names = {
  borrower: "Borrower",
  lender: "Lender",
  lifecycle: "Lifecycle",
  borrowerAdapter: "Borrower FIX",
  lenderAdapter: "Lender FIX",
  market: "Market data",
};
const ports = {
  borrower: 8081,
  lender: 8084,
  lifecycle: 8085,
  borrowerAdapter: 8082,
  lenderAdapter: 8083,
  market: 8080,
};
function saved() {
  try {
    return JSON.parse(localStorage.getItem("lending-lab-context")) || {};
  } catch {
    return {};
  }
}
const labelOf = (key) =>
  key.replace(/([A-Z])/g, " $1").replace(/^./, (s) => s.toUpperCase());
const json = (value) => JSON.stringify(value, null, 2);
export default function App() {
  const [context, setContext] = useState(() => ({
    borrowerOrg: "",
    lenderOrg: "",
    borrowerUser: "",
    lenderUser: "",
    ...saved(),
  }));
  const [side, setSide] = useState("borrower"),
    [category, setCategory] = useState("Setup"),
    [operation, setOperation] = useState(null),
    [fields, setFields] = useState({}),
    [resourceId, setResourceId] = useState(""),
    [key, setKey] = useState(""),
    [page, setPage] = useState(0),
    [size, setSize] = useState(25),
    [result, setResult] = useState(null),
    [history, setHistory] = useState([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [health, setHealth] = useState({}),
    [directories, setDirectories] = useState({}),
    [users, setUsers] = useState({}),
    [roles, setRoles] = useState({}),
    [checked, setChecked] = useState({}),
    [tab, setTab] = useState("actions"),
    [search, setSearch] = useState(""),
    [inspected, setInspected] = useState(null),
    [confirm, setConfirm] = useState(false),
    [quickWorkflow, setQuickWorkflow] = useState({
      agreementId: "",
      reservationId: "",
      securityId: "1",
      quantity: "100",
      collateralCashAmount: "150000",
    });
  const org = context[side + "Org"],
    selectedUserId = context[side + "User"],
    selectedUser = (users[side] || []).find(
      (u) => String(u.id) === String(selectedUserId),
    ),
    selectedUserRole =
      (roles[side] || []).find(
        (r) => String(r.id) === String(selectedUser?.roleId),
      )?.roleName || selectedUser?.role || null,
    available = actions.filter(
      (a) =>
        a.category === category &&
        (a.side === side ||
          (category === "Operations" && a.side === side + "Adapter")) &&
        (!a.requiresAdmin || selectedUserRole === "ADMIN") &&
        a.label.toLowerCase().includes(search.toLowerCase()),
    );
  function updateContext(patch) {
    setContext((old) => {
      const next = { ...old, ...patch };
      localStorage.setItem("lending-lab-context", JSON.stringify(next));
      return next;
    });
  }
  function choose(a) {
    setOperation(a);
    setFields(
      Object.fromEntries(
        Object.entries(defaults(a)).map(([k, v]) => [
          k,
          typeof v === "object" && v !== null ? json(v) : v,
        ]),
      ),
    );
    setResourceId("");
    setKey(a.idempotency ? crypto.randomUUID() : "");
    setPage(0);
    setResult(null);
    setInspected(null);
    setError("");
    setConfirm(false);
  }
  function changeSide(value) {
    setSide(value);
    setOperation(null);
    setResult(null);
    setError("");
  }
  function changeCategory(value) {
    setCategory(value);
    setOperation(null);
    setResult(null);
    setError("");
    setTab("actions");
    setSearch("");
  }
  function body() {
    if (operation.body === undefined) return undefined;
    const template = defaults(operation);
    const result = Object.fromEntries(
      Object.entries(fields).map(([k, v]) => {
        const original = template[k];
        if (typeof original === "object" && original !== null)
          return [k, JSON.parse(v)];
        if (typeof original === "number") {
          if (v === "" || !Number.isFinite(Number(v)))
            throw Error(`${labelOf(k)} must be a number.`);
          return [k, Number(v)];
        }
        if (typeof original === "boolean")
          return [k, v === true || v === "true"];
        if (original === null) {
          if (v === null || v === "" || v === "null") return [k, null];
          if (!Number.isFinite(Number(v)))
            throw Error(`${labelOf(k)} must be a number or null.`);
          return [k, Number(v)];
        }
        return [k, v];
      }),
    );
    if (operation.requiresAdmin) {
      if (!selectedUserId)
        throw Error("Select an admin user before approving the deposit.");
      if (selectedUserRole !== "ADMIN")
        throw Error("Only an active admin user can approve a deposit.");
      return { ...result, userId: Number(selectedUserId) };
    }
    return result;
  }
  function request() {
    if (!operation) throw Error("Select a test action.");
    return {
      side: operation.side,
      path: requestPath(
        operation,
        context[operation.side + "Org"],
        resourceId,
        page,
        size,
      ),
      method: operation.method,
      body: body(),
      headers: operation.idempotency ? { "Idempotency-Key": key } : undefined,
    };
  }
  function remember(r) {
    setResult(r);
    setHistory((old) => [r, ...old].slice(0, 100));
    setInspected(null);
  }
  async function execute(e) {
    e?.preventDefault();
    setError("");
    let req;
    try {
      req = request();
      if (operation.idempotency && !key.trim())
        throw Error("Idempotency-Key is required.");
    } catch (ex) {
      setError(ex.message);
      return;
    }
    setBusy(true);
    try {
      const r = await sendRequest(req);
      remember(r);
      if (r.ok) {
        const data = unwrap(r.payload);
        if (operation.capture && data?.id)
          updateContext({ [operation.capture]: String(data.id) });
        if (
          operation.path === "/api/v1/organizations" &&
          operation.method === "GET"
        )
          setDirectories((old) => ({
            ...old,
            [operation.side]: rowsOf(r.payload),
          }));
        if (operation.path === "/users" && operation.method === "GET")
          setUsers((old) => ({ ...old, [side]: rowsOf(r.payload) }));
        if (operation.csv) {
          const url = URL.createObjectURL(
            new Blob([r.payload], { type: "text/csv" }),
          );
          const link = document.createElement("a");
          link.href = url;
          link.download = "borrower-statement.csv";
          link.click();
          URL.revokeObjectURL(url);
        }
      }
    } catch (ex) {
      setError(
        ex.name === "TimeoutError"
          ? "Request timed out. Check the service and inspect its logs before retrying a write."
          : ex.message +
              " — check that the service is running and the Vite proxy target is correct.",
      );
    } finally {
      setBusy(false);
      setConfirm(false);
    }
  }
  async function checkHealth() {
    setBusy(true);
    setError("");
    const results = await Promise.all(
      Object.keys(ports).map(async (service) => {
        try {
          const r = await sendRequest({
            side: service,
            path: service.includes("Adapter")
              ? "/api/v1/adapter/status"
              : service === "market"
                ? "/api/v1/security-master/get-all-securities"
                : service === "lifecycle"
                  ? "/actuator/health"
                  : "/api/v1/organizations",
          });
          return [service, { ok: r.ok, status: r.status }];
        } catch {
          return [service, { ok: false, status: "unreachable" }];
        }
      }),
    );
    setHealth(Object.fromEntries(results));
    setBusy(false);
  }
  async function loadIdentity() {
    setBusy(true);
    setError("");
    try {
      const r = await sendRequest({ side, path: "/api/v1/organizations" });
      if (!r.ok)
        throw Error(
          "Could not load organizations. Inspect the service response.",
        );
      setDirectories((old) => ({ ...old, [side]: rowsOf(r.payload) }));
      const roleResponse = await sendRequest({
        side,
        path: "/api/v1/organizations/roles",
      });
      if (roleResponse.ok)
        setRoles((old) => ({ ...old, [side]: rowsOf(roleResponse.payload) }));
      if (org) {
        const u = await sendRequest({
          side,
          path: `/api/v1/organizations/${org}/users?size=200`,
        });
        if (!u.ok) throw Error("Could not load users for this organization.");
        setUsers((old) => ({ ...old, [side]: rowsOf(u.payload) }));
      }
    } catch (ex) {
      setError(ex.message);
    } finally {
      setBusy(false);
    }
  }
  async function smoke() {
    setBusy(true);
    setError("");
    for (const service of ["borrower", "lender"]) {
      const id = context[service + "Org"];
      if (!/^[1-9]\d*$/.test(id)) continue;
      for (const resource of [
        "dashboard",
        "agreements",
        "locates",
        "reservations",
        "loans",
        "collateral",
        "margin-calls",
        "recalls",
        "fees",
        "rebates",
        "defaults",
        "buy-ins",
      ]) {
        try {
          const r = await sendRequest({
            side: service,
            path: `/api/v1/organizations/${id}/${resource}`,
          });
          remember(r);
        } catch (ex) {
          remember({
            ok: false,
            status: "network",
            side: service,
            method: "GET",
            path: resource,
            payload: { error: ex.message },
            timestamp: new Date().toISOString(),
          });
        }
      }
    }
    setBusy(false);
    setTab("history");
  }

  async function workflowRequest(label, request) {
    const response = await sendRequest(request);
    remember(response);
    if (!response.ok) throw Error(`${label} failed.`);
    return response;
  }

  async function waitForWorkflowRow(side, path, match, label) {
    for (let attempt = 0; attempt < 20; attempt += 1) {
      const response = await sendRequest({ side, path });
      if (response.ok) {
        const row = rowsOf(response.payload).find(match);
        if (row) return row;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 600));
    }
    throw Error(`${label} did not arrive within 12 seconds. Confirm Kafka, both FIX adapters, and both services are running, then retry.`);
  }

  async function runCompleteLoanWorkflow() {
    setBusy(true);
    setError("");
    try {
      const borrowerOrg = Number(context.borrowerOrg);
      const lenderOrg = Number(context.lenderOrg);
      const securityId = Number(quickWorkflow.securityId);
      const quantity = Number(quickWorkflow.quantity);
      const collateralCashAmount = Number(quickWorkflow.collateralCashAmount);
      if (!borrowerOrg || !lenderOrg)
        throw Error("Select both borrower and lender organization IDs first.");
      if (![securityId, quantity, collateralCashAmount].every(Number.isFinite) || quantity <= 0 || collateralCashAmount <= 0)
        throw Error("Security ID, quantity, and collateral cash must be positive numbers.");

      const agreements = await workflowRequest("Loading borrower agreements", {
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/agreements`,
      });
      const requestedAgreementId = Number(quickWorkflow.agreementId);
      const agreement = rowsOf(agreements.payload).find(
        (row) =>
          Number(row.id) === requestedAgreementId ||
          (!requestedAgreementId && String(row.status || "").toUpperCase() === "ACTIVE"),
      );
      if (!agreement || String(agreement.status || "").toUpperCase() !== "ACTIVE")
        throw Error("An active borrower MSLA is required. Approve and wait for the MSLA before running this workflow.");

      const locateResponse = await workflowRequest("Borrower locate request", {
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/locates`,
        method: "POST",
        body: { agreementId: Number(agreement.id), securityId, quantity, expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString() },
      });
      const borrowerLocateId = Number(unwrap(locateResponse.payload)?.id);
      const lenderLocate = await waitForWorkflowRow("lender", `/api/v1/organizations/${lenderOrg}/locates`, (row) => Number(row.borrowerLocateId) === borrowerLocateId, "Lender locate");
      await workflowRequest("Lender locate approval", {
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/locates/${lenderLocate.id}/approve`,
        method: "POST",
        body: { quantity, reason: "Complete demo workflow" },
      });
      await waitForWorkflowRow("borrower", `/api/v1/organizations/${borrowerOrg}/locates`, (row) => Number(row.id) === borrowerLocateId && ["APPROVED", "PARTIALLY_APPROVED"].includes(String(row.status || "").toUpperCase()), "Borrower locate approval");

      const reservationResponse = await workflowRequest("Borrower reservation request", {
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/locates/${borrowerLocateId}/reserve`,
        method: "POST",
        body: { quantity },
      });
      const borrowerReservationId = Number(unwrap(reservationResponse.payload)?.id);
      const lenderReservation = await waitForWorkflowRow("lender", `/api/v1/organizations/${lenderOrg}/reservations`, (row) => Number(row.borrowerReservationId) === borrowerReservationId, "Lender reservation");
      await workflowRequest("Lender reservation approval", {
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/reservations/${lenderReservation.id}/approve`,
        method: "POST",
        body: { quantity, reason: "Complete demo workflow" },
      });
      await waitForWorkflowRow("borrower", `/api/v1/organizations/${borrowerOrg}/reservations`, (row) => Number(row.id) === borrowerReservationId && String(row.status || "").toUpperCase() === "ACTIVE", "Borrower active reservation");

      const loanResponse = await workflowRequest("Borrower loan request", {
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/loans`,
        method: "POST",
        body: { reservationId: borrowerReservationId, quantity, collateral: [{ assetType: "CASH", securityId: null, quantity: 0, cashAmount: collateralCashAmount, marginCallId: null }] },
      });
      const borrowerLoanId = Number(unwrap(loanResponse.payload)?.id);
      const lenderLoan = await waitForWorkflowRow("lender", `/api/v1/organizations/${lenderOrg}/loans`, (row) => Number(row.borrowerLoanId) === borrowerLoanId, "Lender loan");
      await workflowRequest("Lender loan approval", {
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/loans/${lenderLoan.id}/approve`,
        method: "POST",
        body: {},
      });
      const lenderCollateral = await waitForWorkflowRow("lender", `/api/v1/organizations/${lenderOrg}/collateral`, (row) => Number(row.loanId) === Number(lenderLoan.id) && String(row.status || "").toUpperCase() === "PLEDGE_PENDING", "Lender collateral pledge");
      await workflowRequest("Lender collateral confirmation", {
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/collateral/${lenderCollateral.id}/confirm`,
        method: "POST",
        body: { reference: `DEMO-PLEDGE-${Date.now()}` },
      });
      const settled = await workflowRequest("Lender loan settlement", {
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/loans/${lenderLoan.id}/settle`,
        method: "POST",
        body: { reference: `DEMO-LOAN-${Date.now()}` },
      });
      await waitForWorkflowRow("borrower", `/api/v1/organizations/${borrowerOrg}/loans`, (row) => Number(row.id) === borrowerLoanId && String(row.status || "").toUpperCase() === "ACTIVE", "Borrower settled loan");
      setQuickWorkflow((old) => ({ ...old, reservationId: String(borrowerReservationId) }));
      setResourceId(String(borrowerLoanId));
      setResult(settled);
      setTab("history");
    } catch (ex) {
      setError(ex.message);
    } finally {
      setBusy(false);
    }
  }
  async function runQuickLoanWorkflow() {
    setBusy(true);
    setError("");
    try {
      const borrowerOrg = Number(context.borrowerOrg);
      const lenderOrg = Number(context.lenderOrg);
      if (!borrowerOrg || !lenderOrg)
        throw Error("Select both borrower and lender organization IDs first.");

      const securityId = Number(quickWorkflow.securityId || 1);
      const requestedQuantity = Number(quickWorkflow.quantity || 100);
      const collateralCashAmount = Number(
        quickWorkflow.collateralCashAmount || 150000,
      );

      const reservationResponse = await sendRequest({
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/reservations`,
      });
      if (!reservationResponse.ok)
        throw Error("Could not load borrower reservations for the loan workflow.");

      const reservations = rowsOf(reservationResponse.payload);
      const manualReservationId = quickWorkflow.reservationId
        ? Number(quickWorkflow.reservationId)
        : null;
      const reservation =
        (manualReservationId !== null
          ? reservations.find((row) => Number(row.id) === manualReservationId)
          : null) ||
        reservations.find(
          (row) =>
            Number(row.securityId) === securityId &&
            Number(row.quantity) > 0 &&
            !["REJECTED", "EXPIRED"].includes(
              String(row.status || "").toUpperCase(),
            ),
        ) ||
        reservations.find(
          (row) =>
            Number(row.quantity) > 0 &&
            !["REJECTED", "EXPIRED"].includes(
              String(row.status || "").toUpperCase(),
            ),
        );
      if (!reservation)
        throw Error(
          manualReservationId !== null
            ? `Reservation ${manualReservationId} was not found or is not usable.`
            : `No usable reservation for security ${securityId} was found. Complete the reservation step first.`,
        );

      const loanBody = {
        reservationId: Number(reservation.id),
        quantity: Number(requestedQuantity || reservation.quantity || 100),
        collateral: [
          {
            assetType: "CASH",
            securityId: null,
            quantity: 0,
            cashAmount: collateralCashAmount,
            marginCallId: null,
          },
        ],
      };
      const loanResponse = await sendRequest({
        side: "borrower",
        path: `/api/v1/organizations/${borrowerOrg}/loans`,
        method: "POST",
        body: loanBody,
      });
      remember(loanResponse);
      if (!loanResponse.ok)
        throw Error("Borrower loan request failed.");

      const borrowerLoanId = Number(unwrap(loanResponse.payload)?.id ?? unwrap(loanResponse.payload)?.loanId);
      if (!Number.isFinite(borrowerLoanId))
        throw Error("Borrower loan response did not include a usable local loan ID.");

      const lenderLoansResponse = await sendRequest({
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/loans`,
      });
      if (!lenderLoansResponse.ok)
        throw Error("Could not load lender loans to find the newly created loan.");

      const lenderLoan = rowsOf(lenderLoansResponse.payload).find(
        (row) => Number(row.borrowerLoanId) === borrowerLoanId,
      );
      if (!lenderLoan)
        throw Error("The borrower loan exists, but the lender has not yet projected it. Wait for the event to arrive and retry.");

      const approveResponse = await sendRequest({
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/loans/${lenderLoan.id}/approve`,
        method: "POST",
        body: {},
      });
      remember(approveResponse);
      if (!approveResponse.ok)
        throw Error("Lender approval failed.");

      const collateralResponse = await sendRequest({
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/collateral`,
      });
      if (!collateralResponse.ok)
        throw Error("Could not read lender collateral to find the pending pledge.");
      const collateralPosition = rowsOf(collateralResponse.payload).find(
        (row) => Number(row.loanId) === Number(lenderLoan.id) && ["PLEDGE_PENDING", "POSTED"].includes(String(row.status || "").toUpperCase()),
      );
      if (!collateralPosition)
        throw Error("No pending lender collateral position was found for the approved loan.");

      const confirmResponse = await sendRequest({
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/collateral/${collateralPosition.id}/confirm`,
        method: "POST",
        body: { reference: `AUTO-LOAN-${Date.now()}` },
      });
      remember(confirmResponse);
      if (!confirmResponse.ok)
        throw Error("Lender collateral receipt confirmation failed.");

      const settlementResponse = await sendRequest({
        side: "lender",
        path: `/api/v1/organizations/${lenderOrg}/loans/${lenderLoan.id}/settle`,
        method: "POST",
        body: { reference: `AUTO-SETTLE-${Date.now()}` },
      });
      remember(settlementResponse);
      if (!settlementResponse.ok)
        throw Error("Lender loan settlement failed.");

      setResult(settlementResponse);
      setResourceId(String(lenderLoan.id));
      setError("");
    } catch (ex) {
      setError(ex.message);
    } finally {
      setBusy(false);
    }
  }
  let preview;
  try {
    preview = operation ? request() : null;
  } catch {
    preview = null;
  }
  const rows = result ? rowsOf(result.payload) : [],
    data = result ? unwrap(result.payload) : null;
  return (
    <div className="app">
      <aside>
        <div className="logo">
          <span>SL</span>
          <div>
            Lending lab<small>BACKEND WORKFLOW TESTER</small>
          </div>
        </div>
        <div className="side-switch">
          {["borrower", "lender"].map((s) => (
            <button
              key={s}
              onClick={() => changeSide(s)}
              className={side === s ? "selected" : ""}
              disabled={busy}
            >
              {names[s]}
            </button>
          ))}
        </div>
        <div className="nav-label">TEST WORKFLOWS</div>
        <nav>
          {categories.map((cat, i) => (
            <button
              key={cat}
              disabled={busy}
              className={category === cat && tab === "actions" ? "active" : ""}
              onClick={() => changeCategory(cat)}
            >
              <span>{String(i + 1).padStart(2, "0")}</span>
              {cat}
            </button>
          ))}
        </nav>
        <div className="side-bottom">
          <button disabled={busy} onClick={() => setTab("guide")}>
            ☷ Guided workflow
          </button>
          <button disabled={busy} onClick={() => setTab("history")}>
            ↗ Request history <b>{history.length}</b>
          </button>
          <p>Real HTTP requests. No simulated approvals or payments.</p>
        </div>
      </aside>
      <main>
        <header>
          <div>
            <span className="pill">LOCAL TEST ENVIRONMENT</span>
            <span>
              {names[side]} service · :{ports[side]}
            </span>
          </div>
          <button className="button" disabled={busy} onClick={checkHealth}>
            Check connections
          </button>
        </header>
        <div className="content">
          <div className="title-row">
            <div>
              <div className="eyebrow">
                SECURITIES LENDING / {names[side].toUpperCase()}
              </div>
              <h1>
                {tab === "guide"
                  ? "End-to-end workflow"
                  : tab === "history"
                    ? "Request history"
                    : category}
              </h1>
              <p>Exercise the real services and inspect every response.</p>
            </div>
            <button
              className="button"
              disabled={busy || (!context.borrowerOrg && !context.lenderOrg)}
              onClick={smoke}
            >
              Run read-only smoke checks
            </button>
          </div>
          <div className="service-strip">
            {Object.keys(ports).map((s) => (
              <div key={s}>
                <i
                  className={health[s] ? (health[s].ok ? "up" : "down") : ""}
                />
                <strong>{names[s]}</strong>
                <span>:{ports[s]}</span>
                <small>
                  {health[s]?.ok
                    ? "Reachable"
                    : health[s]?.status || "Not checked"}
                </small>
              </div>
            ))}
          </div>
          <section className="identity">
            <div>
              <strong>Test identity</strong>
              <small>
                Context selection only — authentication is not implemented.
              </small>
            </div>
            <label>
              Organization ID
              <input
                aria-label="Organization ID"
                type="number"
                min="1"
                value={org}
                disabled={busy}
                onChange={(e) => {
                  updateContext({
                    [side + "Org"]: e.target.value,
                    [side + "User"]: "",
                  });
                  setUsers((old) => ({ ...old, [side]: [] }));
                }}
              />
            </label>
            <label>
              Registered organization
              <select
                aria-label="Registered organization"
                value={org}
                disabled={busy}
                onChange={(e) => {
                  updateContext({
                    [side + "Org"]: e.target.value,
                    [side + "User"]: "",
                  });
                  setUsers((old) => ({ ...old, [side]: [] }));
                }}
              >
                <option value="">Select organization</option>
                {org &&
                  !(directories[side] || []).some(
                    (o) => String(o.id) === String(org),
                  ) && (
                    <option value={org}>
                      Current organization #{org} · reload directory
                    </option>
                  )}
                {(directories[side] || []).map((o) => (
                  <option key={o.id} value={o.id}>
                    {o.name} · #{o.id}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Test as user
              <select
                value={context[side + "User"]}
                disabled={busy}
                onChange={(e) =>
                  updateContext({ [side + "User"]: e.target.value })
                }
              >
                <option value="">Select registered user</option>
                {(users[side] || [])
                  .filter((u) => u.active !== false)
                  .map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.name} · {u.email}
                    </option>
                  ))}
              </select>
            </label>
            <button className="button" disabled={busy} onClick={loadIdentity}>
              Load organizations / users
            </button>
          </section>
          {error && (
            <div className="notice error" role="alert">
              {error}
              <button onClick={() => setError("")}>×</button>
            </div>
          )}
          {category === "Setup" && tab === "actions" && (
            <div className="notice">
              <strong>
                Registration is available. Password login is pending.
              </strong>
              <span>
                The backend has no login, token, password, or role-enforcement
                endpoints. Register organizations and users below, then choose a
                test identity above. This selection does not authenticate or
                authorize requests.
              </span>
              <button disabled title="No backend login endpoint exists">
                Login unavailable
              </button>
            </div>
          )}
          {[
            "Collateral & risk",
            "Margin & recalls",
            "Billing & payments",
            "Operations",
          ].includes(category) &&
            tab === "actions" && (
              <div className="notice owner">
                <strong>Lifecycle/payment owner required</strong>
                <span>
                  Start lifecycle on port 8085 for margin calls, recalls, and
                  daily cash rebates and lending fees. Cash rebates settle in
                  the demo ledger. Lending fee bills appear on the MSLA
                  schedule, and payment confirmation follows the existing
                  payment workflow.
                </span>
              </div>
            )}
          {tab === "guide" ? (
            <section className="panel guide">
              <div className="panel-title">
                <h2>Follow a request across both platforms</h2>
                <span>
                  {Object.values(checked).filter(Boolean).length} /{" "}
                  {workflow.length} manually verified
                </span>
              </div>
              <p className="help">
                Check a step only after inspecting its resulting business state.
                An HTTP 200 can mean queued or pending. Local borrower and
                lender resource IDs may differ.
              </p>
              {workflow.map(([cat, text, kind], i) => (
                <div className="guide-step" key={text}>
                  <input
                    type="checkbox"
                    aria-label={`Verify step ${i + 1}`}
                    checked={Boolean(checked[i])}
                    onChange={(e) =>
                      setChecked((old) => ({ ...old, [i]: e.target.checked }))
                    }
                  />
                  <span className="step-number">{i + 1}</span>
                  <div>
                    <strong>{text}</strong>
                    <small>{kind}</small>
                  </div>
                  <button
                    className="text-button"
                    onClick={() => changeCategory(cat)}
                  >
                    Open →
                  </button>
                </div>
              ))}
              <div className="notice">
                <strong>Kafka topic setup</strong>
                <span>
                  In the Kafka portal click “Set up lending topics”. These
                  backends have no NewTopic beans, and the managed broker has
                  auto topic creation disabled. Kafka is enabled in the services’
                  application.properties. Start both FIX adapters with run-lender.cmd
                  and run-borrower.cmd and their configured databases. No environment
                  variables are required. The market service currently provides
                  HTTP data, so importing securities does not require Kafka.
                </span>
              </div>
            </section>
          ) : tab === "history" ? (
            <section className="panel">
              <div className="panel-title">
                <h2>Requests made in this browser session</h2>
                <div>
                  <button
                    className="text-button"
                    onClick={() => {
                      const url = URL.createObjectURL(
                        new Blob([json(history)], { type: "application/json" }),
                      );
                      const a = document.createElement("a");
                      a.href = url;
                      a.download = "lending-test-results.json";
                      a.click();
                      URL.revokeObjectURL(url);
                    }}
                  >
                    Export results
                  </button>
                  <button
                    className="text-button"
                    onClick={() => {
                      setHistory([]);
                      setResult(null);
                    }}
                  >
                    Clear history
                  </button>
                </div>
              </div>
              <div className="history-layout">
                <div className="history-list">
                  {history.map((r, i) => (
                    <button
                      key={i}
                      onClick={() => {
                        setResult(r);
                        setInspected(null);
                      }}
                    >
                      <span
                        className={"status " + (r.ok ? "success" : "failure")}
                      >
                        {r.status}
                      </span>
                      <div>
                        <strong>
                          {names[r.side]} · {r.method}
                        </strong>
                        <small>{r.path}</small>
                      </div>
                      <time>{new Date(r.timestamp).toLocaleTimeString()}</time>
                    </button>
                  ))}
                  {!history.length && (
                    <Empty text="No requests yet. Select a workflow action to start testing." />
                  )}
                </div>
                <Response result={result} inspected={inspected} />
              </div>
            </section>
          ) : (
            <div className="test-layout">
              <section className="panel action-list">
                <div className="panel-title">
                  <h2>Available tests</h2>
                  <span>{available.length}</span>
                </div>
                <input
                  aria-label="Search actions"
                  className="action-search"
                  placeholder="Find an action…"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
                {available.map((a) => (
                  <button
                    key={a.id}
                    disabled={busy}
                    onClick={() => choose(a)}
                    className={operation?.id === a.id ? "chosen" : ""}
                  >
                    <span className={"method " + a.method.toLowerCase()}>
                      {a.method}
                    </span>
                    <div>
                      {a.label}
                      <small>{names[a.side]}</small>
                    </div>
                    <span>›</span>
                  </button>
                ))}
                {!available.length && (
                  <Empty text="No actions for this side in this workflow. Switch platforms to continue." />
                )}
              </section>
              <div className="test-detail">
                {side === "borrower" && category === "Loans & returns" && (
                  <section className="panel request-panel">
                    <div className="panel-title">
                      <div>
                        <h2>Loan workflow automation</h2>
                        <small>Locate, reserve, loan, collateral, and settlement</small>
                      </div>
                    </div>
                    <div className="form-grid">
                      <label>
                        Active MSLA ID
                        <input
                          type="number"
                          value={quickWorkflow.agreementId}
                          onChange={(e) =>
                            setQuickWorkflow((old) => ({
                              ...old,
                              agreementId: e.target.value,
                            }))
                          }
                          placeholder="optional active agreement"
                        />
                      </label>
                      <label>
                        Reserve ID
                        <input
                          type="number"
                          value={quickWorkflow.reservationId}
                          onChange={(e) =>
                            setQuickWorkflow((old) => ({
                              ...old,
                              reservationId: e.target.value,
                            }))
                          }
                          placeholder="optional"
                        />
                      </label>
                      <label>
                        Security ID
                        <input
                          type="number"
                          value={quickWorkflow.securityId}
                          onChange={(e) =>
                            setQuickWorkflow((old) => ({
                              ...old,
                              securityId: e.target.value,
                            }))
                          }
                        />
                      </label>
                      <label>
                        Quantity
                        <input
                          type="number"
                          value={quickWorkflow.quantity}
                          onChange={(e) =>
                            setQuickWorkflow((old) => ({
                              ...old,
                              quantity: e.target.value,
                            }))
                          }
                        />
                      </label>
                      <label>
                        Collateral cash
                        <input
                          type="number"
                          value={quickWorkflow.collateralCashAmount}
                          onChange={(e) =>
                            setQuickWorkflow((old) => ({
                              ...old,
                              collateralCashAmount: e.target.value,
                            }))
                          }
                        />
                      </label>
                    </div>
                    <div className="request-actions">
                      <span>Requires an active MSLA, lender inventory, and borrower cash</span>
                      <button
                        type="button"
                        className="button quick-flow"
                        disabled={busy}
                        onClick={runCompleteLoanWorkflow}
                      >
                        Complete locate-to-loan workflow
                      </button>
                      <button
                        type="button"
                        className="button quick-flow"
                        disabled={busy}
                        onClick={runQuickLoanWorkflow}
                      >
                        Use existing reservation only
                      </button>
                    </div>
                  </section>
                )}
                {operation ? (
                  <section className="panel request-panel">
                    <div className="panel-title">
                      <div>
                        <h2>{operation.label}</h2>
                        <small>{names[operation.side]} endpoint</small>
                      </div>
                      <span
                        className={"method " + operation.method.toLowerCase()}
                      >
                        {operation.method}
                      </span>
                    </div>
                    <div className="endpoint">
                      {preview?.path || operation.path}
                    </div>
                    {operation.note && (
                      <p className="operation-note">{operation.note}</p>
                    )}
                    <form onSubmit={execute}>
                      <fieldset disabled={busy}>
                        <div className="form-grid">
                          {operation.path.includes("{id}") && (
                            <label>
                              Resource ID{" "}
                              <small>
                                Use the {names[operation.side]} service’s local
                                ID
                              </small>
                              <input
                                type="number"
                                min="1"
                                required
                                value={resourceId}
                                onChange={(e) => setResourceId(e.target.value)}
                              />
                            </label>
                          )}
                          {operation.paged && (
                            <>
                              <label>
                                Page (zero based)
                                <input
                                  type="number"
                                  min="0"
                                  required
                                  value={page}
                                  onChange={(e) =>
                                    setPage(Number(e.target.value))
                                  }
                                />
                              </label>
                              <label>
                                Page size
                                <input
                                  type="number"
                                  min="1"
                                  max="200"
                                  required
                                  value={size}
                                  onChange={(e) =>
                                    setSize(Number(e.target.value))
                                  }
                                />
                              </label>
                            </>
                          )}
                          {Object.entries(fields).map(([field, value]) => {
                            const original = defaults(operation)[field],
                              nested =
                                original !== null &&
                                typeof original === "object";
                            return (
                              <label
                                key={field}
                                className={nested ? "full" : ""}
                              >
                                {labelOf(field)}
                                {operation.choices?.[field] ? (
                                  <select value={value} onChange={(e) =>
                                    setFields((old) => ({ ...old, [field]: e.target.value }))}>
                                    <option value="">Default (at loan termination)</option>
                                    {operation.choices[field].map((choice) => (
                                      <option key={choice} value={choice}>{choice}</option>
                                    ))}
                                  </select>
                                ) : nested ? (
                                  <>
                                    <small>
                                      Editable JSON; use actual security IDs.
                                      CASH uses null securityId; SECURITY
                                      requires its securityId.
                                    </small>
                                    <textarea
                                      rows={field === "collateral" ? 10 : 8}
                                      value={value}
                                      onChange={(e) =>
                                        setFields((old) => ({
                                          ...old,
                                          [field]: e.target.value,
                                        }))
                                      }
                                    />
                                  </>
                                ) : typeof original === "boolean" ? (
                                  <select
                                    value={String(value)}
                                    onChange={(e) =>
                                      setFields((old) => ({
                                        ...old,
                                        [field]: e.target.value === "true",
                                      }))
                                    }
                                  >
                                    <option value="true">true</option>
                                    <option value="false">false</option>
                                  </select>
                                ) : (
                                  <input
                                    type={
                                      typeof original === "number"
                                        ? "number"
                                        : field.toLowerCase().includes("email")
                                          ? "email"
                                          : "text"
                                    }
                                    step="any"
                                    required={original !== null}
                                    value={value ?? ""}
                                    placeholder={
                                      original === null ? "null (optional)" : ""
                                    }
                                    onChange={(e) =>
                                      setFields((old) => ({
                                        ...old,
                                        [field]: e.target.value,
                                      }))
                                    }
                                  />
                                )}
                              </label>
                            );
                          })}
                          {operation.idempotency && (
                            <label className="full">
                              Idempotency-Key
                              <input
                                required
                                value={key}
                                onChange={(e) => setKey(e.target.value)}
                              />
                              <small>
                                Keep this key for retries; generate a new key
                                only for a new payment request.
                              </small>
                            </label>
                          )}
                        </div>
                        <details className="request-preview">
                          <summary>Inspect outgoing request</summary>
                          <pre>
                            {json(
                              preview || {
                                message:
                                  "Select organization and resource IDs to preview.",
                              },
                            )}
                          </pre>
                        </details>
                        {operation.method !== "GET" && (
                          <label className="confirm-write">
                            <input
                              type="checkbox"
                              checked={confirm}
                              onChange={(e) => setConfirm(e.target.checked)}
                            />{" "}
                            Send this write to the real development backend
                          </label>
                        )}
                        <div className="request-actions">
                          <span>
                            {operation.method === "GET"
                              ? "Read-only HTTP check"
                              : "Success does not imply final settlement"}
                          </span>
                          <button
                            className="button primary"
                            type="submit"
                            disabled={
                              busy || (operation.method !== "GET" && !confirm)
                            }
                          >
                            {busy
                              ? "Request in progress…"
                              : operation.csv
                                ? "Download CSV"
                                : "Send request →"}
                          </button>
                        </div>
                      </fieldset>
                    </form>
                  </section>
                ) : (
                  <section className="panel">
                    <Empty text="Choose an action to load its request form. Start with registration, then follow the guided workflow." />
                    <button
                      className="button guide-link"
                      onClick={() => setTab("guide")}
                    >
                      Open guided workflow →
                    </button>
                  </section>
                )}
                {result && (
                  <section className="panel">
                    <div className="panel-title">
                      <h2>Actual service response</h2>
                      <div>
                        <span
                          className={
                            "status " + (result.ok ? "success" : "failure")
                          }
                        >
                          {result.status}
                        </span>
                        <span className="muted">
                          {" "}
                          {result.duration ?? "—"} ms
                        </span>
                      </div>
                    </div>
                    {result.ok && operation?.method !== "GET" && (
                      <p className="operation-note">
                        Request accepted by this endpoint. Inspect status and
                        refresh both sides to verify asynchronous completion.
                      </p>
                    )}
                    {rows.length > 0 && (
                      <DataTable
                        rows={rows}
                        inspect={(row) => setInspected(row)}
                        selectId={(row) => {
                          setResourceId(String(row.id ?? ""));
                          setInspected(row);
                        }}
                      />
                    )}
                    {data?.totalPages !== undefined && (
                      <div className="pagination">
                        <span>
                          Page {data.page + 1} / {data.totalPages} ·{" "}
                          {data.totalElements} records
                        </span>
                        <button
                          className="text-button"
                          disabled={busy || page === 0}
                          onClick={() => {
                            setPage((p) => p - 1);
                          }}
                        >
                          Previous page
                        </button>
                        <button
                          className="text-button"
                          disabled={busy || page + 1 >= data.totalPages}
                          onClick={() => setPage((p) => p + 1)}
                        >
                          Next page
                        </button>
                        <small>Change page, then Send request.</small>
                      </div>
                    )}
                    <Response result={result} inspected={inspected} />
                  </section>
                )}
              </div>
            </div>
          )}
          <footer>
            <span>
              Browser → Vite proxy → Spring backend → Kafka / FIX → counterparty
            </span>
            <span>
              {busy
                ? "Request in progress"
                : "No background writes or fabricated results"}
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}
function Empty({ text }) {
  return (
    <div className="empty">
      <span>⇄</span>
      <p>{text}</p>
    </div>
  );
}
function Response({ result, inspected }) {
  return result ? (
    <div className="response">
      <div className="response-meta">
        <span>{inspected ? "SELECTED RECORD" : "RAW RESPONSE"}</span>
        <time>{new Date(result.timestamp).toLocaleTimeString()}</time>
      </div>
      <pre>
        {typeof (inspected || result.payload) === "string"
          ? inspected || result.payload
          : json(inspected || result.payload)}
      </pre>
    </div>
  ) : (
    <Empty text="Run a request to inspect its HTTP response." />
  );
}
function DataTable({ rows, inspect, selectId }) {
  const columns = [...new Set(rows.flatMap((r) => Object.keys(r || {})))].slice(
    0,
    9,
  );
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c}>{labelOf(c)}</th>
            ))}
            <th>Inspect</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={row.id ?? i}>
              {columns.map((c) => (
                <td
                  key={c}
                  title={
                    typeof row[c] === "object"
                      ? json(row[c])
                      : String(row[c] ?? "")
                  }
                >
                  {typeof row[c] === "object"
                    ? json(row[c])
                    : String(row[c] ?? "—")}
                </td>
              ))}
              <td>
                <button className="text-button" onClick={() => inspect(row)}>
                  View JSON
                </button>
                {row.id && (
                  <button className="text-button" onClick={() => selectId(row)}>
                    Use ID {row.id}
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
