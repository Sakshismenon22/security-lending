import { lenderGet, lenderPost } from "./lendDeskApi";

const label = (prefix, id) =>
  id == null ? "—" : `${prefix}-${String(id).padStart(6, "0")}`;
const pending = (status) => ["PENDING", "ACCEPTED"].includes(status);
const failed = (status) => ["FAILED", "REJECTED", "CANCELLED"].includes(status);
export const requestPending = (record) => pending(record.request?.status);
export const canRequestBuyin = (record) =>
  record.rawStatus === "OPEN" &&
  ["RECALL", "MATURITY"].includes(record.obligationType) &&
  !requestPending(record) &&
  !record.reports.some((report) => !failed(report.status));

export const canReportBuyin = (record) =>
  record.rawStatus === "OPEN" &&
  record.request?.status === "ACCEPTED" &&
  !pending(record.executionRequest?.status) &&
  !(record.buyinOrders || []).some((order) =>
    ["PENDING", "EXECUTION_PENDING", "FILLED"].includes(order.status),
  ) &&
  !record.reports.some((report) => !failed(report.status));

export function liveDefaultRecord(row) {
  const def = row.default,
    reported = row.reportedData || {},
    recall = row.recall,
    margin = row.marginCall;
  const reports = row.buyIns || [],
    requests = row.requests || [],
    request =
      requests.find((item) => item.type !== "BUY_IN_EXECUTION_REQUESTED") ||
      null;
  const executionRequest =
    requests.find((item) => item.type === "BUY_IN_EXECUTION_REQUESTED") || null;
  const activeReport = reports.find((report) => !failed(report.status));
  const resolved = ["RESOLVED", "CLOSED", "SETTLED"].includes(def.status);
  const quantity =
    reported.quantity ??
    (recall ? Math.max(0, recall.quantity - recall.fulfilledQuantity) : null);
  const exposure =
    reported.obligationValue ??
    reported.exposure ??
    (margin
      ? Math.max(0, Number(margin.amount) - Number(margin.fulfilledAmount))
      : null);
  return {
    live: true,
    backendId: def.id,
    ownerReferenceId: def.ownerReferenceId,
    id: label("DEF", def.id),
    loanId: label("LOAN", row.loan.id),
    backendLoanId: row.loan.id,
    loanStatus: row.loan.status,
    loanClosedAt: row.loan.closedAt,
    borrower: row.borrowerName || "—",
    security: row.security?.symbol || "—",
    quantity,
    price: null,
    exposure: exposure == null ? null : Number(exposure),
    trigger: def.obligationType.replaceAll("_", " "),
    reason: def.reason,
    deadline: reported.dueAt || recall?.dueAt || margin?.dueAt || null,
    rawStatus: def.status,
    obligationType: def.obligationType,
    executionRequest,
    status: resolved
      ? "Resolved"
      : def.status === "OPEN"
        ? "In default"
        : def.status.replaceAll("_", " "),
    recovery: "Not reported",
    reference: label(
      def.obligationType.includes("MARGIN")
        ? "MC"
        : def.obligationType.includes("RECALL")
          ? "REC"
          : "OBL",
      def.obligationId,
    ),
    notices: [],
    reports,
    buyinOrders: row.orders || [],
    marketPrice: row.security?.price ?? null,
    quoteAt: row.security?.priceAt ?? null,
    request,
    requests,
    reported,
    order: activeReport
      ? {
          buyinId: label("BI", activeReport.id),
          id: label("BI", activeReport.id),
          status: activeReport.status,
          quantity: activeReport.quantity,
          filled: null,
        }
      : null,
    activities: [
      ...(row.orders || []).map((order) => ({
        time: order.filledAt || order.createdAt,
        event: `Buy-in order #${order.id} · ${order.status}`,
        reference: order.failureReason || order.executionReference,
      })),
      ...reports
        .filter((report) => report.inventoryReceivedAt)
        .map((report) => ({
          time: report.inventoryReceivedAt,
          event: "Purchased securities received into inventory",
          reference: report.inventoryReference,
        })),
      {
        time: def.createdAt,
        event: `Default reported · ${def.status}`,
        reference: `Owner reference ${def.ownerReferenceId}`,
      },
      ...requests.map((item) => ({
        time: item.createdAt,
        event: `Buy-in request · ${item.status}`,
        reference: item.failureReason || label("REQ", item.id),
      })),
      ...reports.map((item) => ({
        time: item.createdAt,
        event: `Buy-in reported · ${item.status}`,
        reference: label("BI", item.id),
      })),
    ].sort((a, b) => String(a.time).localeCompare(String(b.time))),
  };
}
export const defaultsBuyinsApi = {
  closeoutPreview: (org, id) =>
    lenderGet(
      `/api/v1/organizations/${org}/defaults/${id}/loan-closeout-preview`,
    ),
  closeLoan: (org, id, reference) =>
    lenderPost(`/api/v1/organizations/${org}/defaults/${id}/close-loan`, {
      reference,
    }),
  placeOrder: (org, id, payload) =>
    lenderPost(
      `/api/v1/organizations/${org}/defaults/${id}/buy-in-orders`,
      payload,
    ),
  cancelOrder: (org, id) =>
    lenderPost(`/api/v1/organizations/${org}/buy-in-orders/${id}/cancel`, {}),
  matchOrder: (org, id) =>
    lenderPost(`/api/v1/organizations/${org}/buy-in-orders/${id}/match`, {}),
  receiveInventory: (org, id, reference) =>
    lenderPost(
      `/api/v1/organizations/${org}/buy-ins/${id}/inventory-receipts`,
      { reference },
    ),
  async snapshot(organizationId, signal) {
    const rows = await lenderGet(
      `/api/v1/organizations/${organizationId}/lenddesk/defaults-buyins`,
      signal,
    );
    if (!Array.isArray(rows))
      throw new Error("The defaults response could not be read. Please retry.");
    return rows.map(liveDefaultRecord);
  },
  report: (organizationId, defaultId, payload) =>
    lenderPost(
      `/api/v1/organizations/${organizationId}/defaults/${defaultId}/buy-in-executions`,
      payload,
    ),
  request: (organizationId, defaultId, clientRequestId) =>
    lenderPost(
      `/api/v1/organizations/${organizationId}/defaults/${defaultId}/buy-in-requests`,
      { clientRequestId },
    ),
};
