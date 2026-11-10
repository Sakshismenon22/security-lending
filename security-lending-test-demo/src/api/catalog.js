// These are HTTP contracts implemented by the checked-in controllers, not mocks.
const op = (id, category, label, side, method, path, body, extra = {}) => ({
  id,
  category,
  label,
  side,
  method,
  path,
  body,
  ...extra,
});
const b = (id, cat, label, path, body, extra) =>
  op(id, cat, label, "borrower", "POST", path, body, extra);
const l = (id, cat, label, path, body, extra) =>
  op(id, cat, label, "lender", "POST", path, body, extra);
const allocation = {
  assetType: "CASH",
  securityId: null,
  quantity: 0,
  cashAmount: 150000,
  marginCallId: null,
};
export const categories = [
  "Setup",
  "Agreements",
  "Locates & reservations",
  "Loans & returns",
  "Collateral & risk",
  "Margin & recalls",
  "Billing & payments",
  "Trading & portfolio",
  "Operations",
];
export const actions = [
  b(
    "register-b",
    "Setup",
    "Register borrower organization",
    "/api/v1/organizations",
    {
      name: "Demo Borrower",
      registrationNumber: "BORROWER_DEMO",
      currency: "INR",
    },
    { capture: "borrowerOrg", global: true },
  ),
  l(
    "register-l",
    "Setup",
    "Register lender organization",
    "/api/v1/organizations",
    {
      name: "Demo Mutual Fund",
      externalId: "LENDER_DEMO",
      publicReferenceId: 1000001,
    },
    { capture: "lenderOrg", global: true },
  ),
  ...["borrower", "lender"].flatMap((side) => [
    op(
      side + "-organizations",
      "Setup",
      "List organizations",
      side,
      "GET",
      "/api/v1/organizations",
      undefined,
      { global: true },
    ),
    op(side + "-user", "Setup", "Register a user", side, "POST", "/users", {
      name: "Demo Admin",
      email: `admin@${side}.example`,
      role: "ADMIN",
    }),
    op(
      side + "-users",
      "Setup",
      "List users / select test identity",
      side,
      "GET",
      "/users",
    ),
    op(
      side + "-update-user",
      "Setup",
      "Update user role / active state",
      side,
      "PATCH",
      "/users/{id}",
      { role: "ADMIN", active: true },
    ),
    op(
      side + "-roles",
      "Setup",
      "List available roles",
      side,
      "GET",
      "/api/v1/organizations/roles",
      undefined,
      { global: true },
    ),
    op(
      side + "-import",
      "Setup",
      "Import market security references",
      side,
      "POST",
      "/api/v1/securities/import",
      undefined,
      { global: true },
    ),
    op(
      side + "-securities",
      "Setup",
      "List securities",
      side,
      "GET",
      "/api/v1/securities",
      undefined,
      { global: true },
    ),
    op(
      side + "-security-refresh",
      "Setup",
      "Refresh completed market price",
      side,
      "POST",
      "/api/v1/securities/{id}/refresh",
      undefined,
      { global: true },
    ),
    op(
      side + "-history",
      "Trading & portfolio",
      "Security OHLC history",
      side,
      "GET",
      "/api/v1/securities/{id}/history",
      undefined,
      { global: true },
    ),
  ]),
  l("connect-borrower", "Setup", "Connect borrower institution", "/borrowers", {
    externalId: "BORROWER_DEMO",
    borrowerOrganizationId: 1,
    name: "Demo Borrower",
  }),
  l("inventory-add", "Setup", "Add lender inventory", "/inventory", {
    securityId: 1,
    quantity: 10000,
    reference: "DEMO-CUSTODY-INITIAL",
  }),
  l(
    "inventory-adjust",
    "Setup",
    "Adjust inventory",
    "/inventory/{id}/adjustments",
    { quantityDelta: 100, reference: "DEMO-CUSTODY-ADJUSTMENT" },
  ),
  b(
    "deposit",
    "Setup",
    "Request cash deposit",
    "/deposits",
    { amount: 1000000, clientRequestId: "$unique" },
    {
      note: "A pending deposit waits for an active administrator to approve it locally before cash becomes available.",
    },
  ),
  b(
    "deposit-approve",
    "Setup",
    "Approve pending deposit",
    "/deposits/{id}/approve",
    { userId: 0 },
    {
      requiresAdmin: true,
      note: "Only the currently selected admin user can approve a pending deposit request.",
    },
  ),
  b(
    "agreement-propose",
    "Agreements",
    "Propose MSLA",
    "/agreements",
    {
      lenderId: 1000001,
      number: "$unique",
      effectiveDate: "$today",
      expiryDate: "$year",
      creditLimit: 10000000,
      feeRate: 0.03,
      rebateRate: 0.01,
      coverageRatio: 1.05,
      billingFrequency: "MONTHLY",
      dayBasis: 365,
      recallAllowed: true,
      marginHours: 24,
      recallHours: 48,
      collateralRules: [
        { assetType: "CASH", securityId: null, haircut: 0 },
        { assetType: "SECURITY", securityId: 2, haircut: 0.1 },
      ],
    },
    {
      note: "Rates are decimals: 0.03 = 3%. Coverage ratio 1.05 = 105%. A 10% haircut is 0.10. Use the lender public reference ID, not its local organization ID.",
    },
  ),
  l(
    "agreement-approve",
    "Agreements",
    "Approve MSLA",
    "/agreements/{id}/approve",
  ),
  l(
    "agreement-reject",
    "Agreements",
    "Reject MSLA",
    "/agreements/{id}/reject",
    { reason: "Demo rejection" },
  ),
  b("locate", "Locates & reservations", "Request locate", "/locates", {
    agreementId: 1,
    securityId: 1,
    quantity: 100,
    expiresAt: "$tomorrow",
  }),
  l(
    "locate-approve",
    "Locates & reservations",
    "Approve locate availability",
    "/locates/{id}/approve",
    { quantity: 100, reason: "Availability verified" },
  ),
  l(
    "locate-reject",
    "Locates & reservations",
    "Reject locate",
    "/locates/{id}/reject",
    { reason: "Insufficient availability" },
  ),
  b(
    "reserve",
    "Locates & reservations",
    "Reserve approved locate",
    "/locates/{id}/reserve",
    { quantity: 100 },
  ),
  l(
    "reservation-approve",
    "Locates & reservations",
    "Approve reservation",
    "/reservations/{id}/approve",
    { quantity: 100, reason: "Reserve inventory" },
  ),
  l(
    "reservation-reject",
    "Locates & reservations",
    "Reject reservation",
    "/reservations/{id}/reject",
    { reason: "Inventory unavailable" },
  ),
  b(
    "reservation-release",
    "Locates & reservations",
    "Release reservation",
    "/reservations/{id}/release",
  ),
  b(
    "loan",
    "Loans & returns",
    "Request loan with collateral",
    "/loans",
    { reservationId: 1, quantity: 100, collateral: [allocation] },
    {
      note: "Requires a confirmed reservation and available borrower cash or owned securities. For security collateral use assetType SECURITY, its securityId, quantity, and cashAmount 0.",
    },
  ),
  l("loan-approve", "Loans & returns", "Approve loan", "/loans/{id}/approve"),
  l("loan-reject", "Loans & returns", "Reject loan", "/loans/{id}/reject", {
    reason: "Risk review declined",
  }),
  l(
    "loan-settle",
    "Loans & returns",
    "Record securities settlement",
    "/loans/{id}/settle",
    { reference: "$unique" },
    {
      note: "Records an externally confirmed custody transfer. This does not execute a real transfer.",
    },
  ),
  b(
    "loan-return",
    "Loans & returns",
    "Request loan return",
    "/loans/{id}/returns",
    { quantity: 100, recallId: null },
  ),
  l(
    "return-settle",
    "Loans & returns",
    "Record return settlement",
    "/returns/{id}/settle",
    { reference: "$unique" },
  ),
  l(
    "return-reject",
    "Loans & returns",
    "Reject return",
    "/returns/{id}/reject",
    { reason: "Transfer not received" },
  ),
  b(
    "collateral-post",
    "Collateral & risk",
    "Provide additional collateral",
    "/loans/{id}/collateral",
    allocation,
  ),
  l(
    "collateral-confirm",
    "Collateral & risk",
    "Confirm collateral receipt",
    "/collateral/{id}/confirm",
    { reference: "$unique" },
  ),
  l(
    "collateral-reject",
    "Collateral & risk",
    "Reject collateral",
    "/collateral/{id}/reject",
    { reason: "Collateral not received" },
  ),
  b(
    "collateral-release",
    "Collateral & risk",
    "Request collateral release",
    "/collateral/{id}/release",
  ),
  l(
    "collateral-release-confirm",
    "Collateral & risk",
    "Confirm collateral release",
    "/collateral/{id}/release-confirmations",
    { reference: "$unique" },
  ),
  l(
    "collateral-release-reject",
    "Collateral & risk",
    "Reject collateral release",
    "/collateral/{id}/release-rejections",
    { reason: "Coverage would be insufficient" },
  ),
  ...["borrower", "lender"].map((side) =>
    op(
      side + "-preview",
      "Collateral & risk",
      "Check indicative collateral coverage",
      side,
      "GET",
      "/loans/{id}/collateral-preview",
      undefined,
      {
        note: "Initial/review preview only. This does not run EOD risk checks or issue margin calls.",
      },
    ),
  ),
  op(
    "valuation",
    "Collateral & risk",
    "Read authoritative loan valuation",
    "borrower",
    "GET",
    "/loans/{id}/valuation",
    undefined,
    { note: "Requires a report from the separate lifecycle owner." },
  ),
  b(
    "margin-respond",
    "Margin & recalls",
    "Respond to margin call with collateral",
    "/margin-calls/{id}/collateral",
    allocation,
  ),
  op(
    "margin-call-manual",
    "Margin & recalls",
    "Send manual margin call",
    "lender",
    "POST",
    "/api/v1/lifecycle/loans/{id}/margin-calls",
    { amount: 250 },
    {
      global: true,
      note: "Demo-only trigger for the lifecycle service. The scheduled job still checks coverage automatically at 3:15 PM and only creates a call when the policy is breached.",
    },
  ),
  l(
    "margin-call-confirm",
    "Margin & recalls",
    "Confirm lender margin call",
    "/margin-calls/{id}/confirm",
    { reference: "$unique" },
    {
      note: "Explicit lender acknowledgement for a lifecycle-generated margin call. This emits the borrower-facing MARGIN_CALL_UPDATED confirmation used by the borrower UI.",
    },
  ),
  l(
    "recall-request",
    "Margin & recalls",
    "Request loan recall",
    "/loans/{id}/recall-requests",
    { quantity: 50, reason: "Portfolio sale", clientRequestId: "$unique" },
    {
      note: "Queues a request to the lifecycle owner. The recall is not issued locally.",
    },
  ),
  b(
    "recall-return",
    "Margin & recalls",
    "Respond to recall with return",
    "/recalls/{id}/returns",
    { quantity: 50, recallId: null },
  ),
  b(
    "fee-payment",
    "Billing & payments",
    "Request lending fee payment",
    "/fees/{id}/payments",
    undefined,
    {
      idempotency: true,
      note: "Reuse the same Idempotency-Key when retrying an uncertain request. Success means a payment request was accepted, not that money settled.",
    },
  ),
  l(
    "rebate-payment",
    "Billing & payments",
    "Request cash-collateral rebate payment",
    "/rebates/{id}/payment-requests",
    { clientRequestId: "$unique" },
    {
      note: "Daily accrual and payment execution belong to the lifecycle/payment owner.",
    },
  ),
  b("withdrawal", "Billing & payments", "Request withdrawal", "/withdrawals", {
    amount: 1000,
    clientRequestId: "$unique",
  }),
  op(
    "statement",
    "Billing & payments",
    "Download account statement",
    "borrower",
    "GET",
    "/statement.csv",
    undefined,
    { csv: true },
  ),
  b(
    "order",
    "Trading & portfolio",
    "Submit paper buy / sell order",
    "/orders",
    {
      securityId: 1,
      side: "BUY",
      type: "MARKET",
      source: "OWNED",
      quantity: 10,
      limitPrice: null,
    },
    {
      note: "SELL / BORROWED sells settled borrowed shares. BUY / BORROWED buys to cover. Execution uses delayed completed market candles.",
    },
  ),
  b(
    "order-cancel",
    "Trading & portfolio",
    "Cancel open order",
    "/orders/{id}/cancel",
  ),
  op(
    "watch-add",
    "Trading & portfolio",
    "Add security to watchlist",
    "borrower",
    "PUT",
    "/watchlist/{id}",
  ),
  op(
    "watch-remove",
    "Trading & portfolio",
    "Remove watchlist security",
    "borrower",
    "DELETE",
    "/watchlist/{id}",
  ),
  l(
    "buyin-request",
    "Operations",
    "Request default buy-in",
    "/defaults/{id}/buy-in-requests",
    { clientRequestId: "$unique" },
  ),
  ...["borrower", "lender"].map((side) =>
    op(
      side + "-notification-read",
      "Operations",
      "Mark notification read",
      side,
      "POST",
      "/notifications/{id}/read",
    ),
  ),
];
const reads = {
  Setup: {
    borrower: ["lenders", "account"],
    lender: ["borrowers", "inventory", "inventory-history", "account"],
  },
  Agreements: {
    borrower: ["agreements", "credit-history"],
    lender: ["agreements"],
  },
  "Locates & reservations": {
    borrower: ["locates", "reservations"],
    lender: ["locates", "reservations"],
  },
  "Loans & returns": {
    borrower: ["loans", "returns"],
    lender: ["loans", "returns"],
  },
  "Collateral & risk": {
    borrower: ["collateral", "collateral/history"],
    lender: ["collateral", "collateral-history", "valuations"],
  },
  "Margin & recalls": {
    borrower: ["margin-calls", "recalls"],
    lender: ["margin-calls", "recalls", "lifecycle-requests"],
  },
  "Billing & payments": {
    borrower: [
      "fees",
      "rebates",
      "rebate-payments",
      "payments",
      "accruals",
      "transactions",
    ],
    lender: [
      "fees",
      "rebates",
      "rebate-payments",
      "accruals",
      "ledger",
      "lifecycle-requests",
    ],
  },
  "Trading & portfolio": {
    borrower: ["orders", "trades", "positions", "holdings", "watchlist"],
    lender: [],
  },
  Operations: {
    borrower: ["dashboard", "notifications", "audit", "defaults", "buy-ins"],
    lender: [
      "dashboard",
      "notifications",
      "audit",
      "defaults",
      "buy-ins",
      "lifecycle-requests",
    ],
  },
};
for (const [category, sides] of Object.entries(reads))
  for (const [side, resources] of Object.entries(sides))
    for (const resource of resources)
      actions.push(
        op(
          `${side}-read-${resource}-${category}`,
          category,
          `View ${resource.replaceAll("-", " ")}`,
          side,
          "GET",
          "/" + resource,
          undefined,
          {
            paged: ![
              "account",
              "dashboard",
              "positions",
              "holdings",
              "watchlist",
              "lenders",
            ].includes(resource),
          },
        ),
      );
for (const side of ["borrower", "lender"])
  for (const resource of ["agreements", "loans"])
    actions.push(
      op(
        `${side}-detail-${resource}`,
        resource === "agreements" ? "Agreements" : "Loans & returns",
        `Read ${resource} detail`,
        side,
        "GET",
        `/${resource}/{id}`,
      ),
    );
for (const side of ["borrowerAdapter", "lenderAdapter"])
  for (const resource of ["status", "outbound", "inbound", "publications"])
    actions.push(
      op(
        `${side}-${resource}`,
        "Operations",
        `${side === "borrowerAdapter" ? "Borrower" : "Lender"} FIX ${resource}`,
        side,
        "GET",
        `/api/v1/adapter/${resource}`,
        undefined,
        { global: true, paged: resource !== "status" },
      ),
    );
for (const side of ["borrowerAdapter", "lenderAdapter"])
  for (const resource of ["outbound", "publications"])
    actions.push(
      op(
        `${side}-retry-${resource}`,
        "Operations",
        `Retry ${side === "borrowerAdapter" ? "borrower" : "lender"} FIX ${resource}`,
        side,
        "POST",
        `/api/v1/adapter/${resource}/{id}/retry`,
        undefined,
        { global: true },
      ),
    );
export const workflow = [
  [
    "Setup",
    "Register both organizations and ADMIN users. Select each organization/user as a test identity.",
    "HTTP",
  ],
  [
    "Setup",
    "Start Kafka, create topics, and start both FIX adapter profiles. Kafka is enabled in application.properties. Check FIX session status.",
    "Transport",
  ],
  [
    "Setup",
    "Connect the borrower institution on the lender. Wait for the borrower lender directory entry.",
    "Async",
  ],
  [
    "Setup",
    "Import security references on both sides, refresh prices, and add lender inventory.",
    "HTTP",
  ],
  [
    "Setup",
    "Request borrower deposit; wait for external payment confirmation before pledging cash.",
    "Owner required",
  ],
  [
    "Agreements",
    "Propose the MSLA on the borrower, approve it on the lender, then verify borrower ACTIVE status.",
    "Async",
  ],
  [
    "Locates & reservations",
    "Request locate, approve availability, request reservation, approve it, then verify confirmation.",
    "Async",
  ],
  [
    "Loans & returns",
    "Request a loan with cash or security collateral. Confirm actual collateral receipt on the lender.",
    "Async",
  ],
  [
    "Loans & returns",
    "Approve the loan, record securities settlement, and verify borrower settled quantity.",
    "Async",
  ],
  [
    "Collateral & risk",
    "Read coverage previews and authoritative valuations separately. Change market price through the market-data owner.",
    "Preview / owner",
  ],
  [
    "Margin & recalls",
    "Lifecycle issues a margin call; borrower supplies collateral; lender confirms receipt; lifecycle resolves it.",
    "Owner required",
  ],
  [
    "Margin & recalls",
    "Lender requests recall; lifecycle issues it; borrower requests return; lender records return settlement.",
    "Owner required",
  ],
  [
    "Billing & payments",
    "Lifecycle accrues fees/rebates; borrower requests fee payment; lender requests rebate payment; verify owner confirmations.",
    "Owner required",
  ],
  [
    "Loans & returns",
    "Return remaining quantity and confirm settlement. Request and confirm collateral release separately.",
    "Async",
  ],
  [
    "Operations",
    "Inspect defaults, buy-ins, deadlines, audit, notifications, adapter inbox/outbox and publications.",
    "Owner / transport",
  ],
];
export function defaults(action, now = new Date()) {
  return action.body === undefined
    ? {}
    : JSON.parse(
        JSON.stringify(action.body)
          .replaceAll("$unique", `DEMO-${crypto.randomUUID()}`)
          .replaceAll("$today", now.toISOString().slice(0, 10))
          .replaceAll(
            "$year",
            new Date(now.getTime() + 365 * 86400000).toISOString().slice(0, 10),
          )
          .replaceAll(
            "$tomorrow",
            new Date(now.getTime() + 86400000).toISOString(),
          ),
      );
}
export function requestPath(action, org, id, page = 0, size = 25) {
  if (!action.global && !/^[1-9]\d*$/.test(String(org)))
    throw Error("Select a valid organization first.");
  if (action.path.includes("{id}") && !/^[1-9]\d*$/.test(String(id)))
    throw Error("Enter the resource ID from this service.");
  let path = action.global
    ? action.path
    : `/api/v1/organizations/${org}${action.path}`;
  path = path.replace("{id}", String(id));
  return path + (action.paged ? `?page=${page}&size=${size}` : "");
}
