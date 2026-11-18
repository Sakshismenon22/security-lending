import BuyinLoanCloseout from "./BuyinLoanCloseout";
import { useRef, useState } from "react";
import { canReportBuyin, defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import { number, rupees, caseDate } from "../data/defaultsBuyinsData";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function BuyinOrderPanel({ record, organizationId, onRefresh }) {
  const [modal, setModal] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [form, setForm] = useState({
    type: "MARKET",
    limitPrice: "",
    costs: "0",
  });
  const sending = useRef(false),
    clientId = useRef(null);
  const update = (key, value) => {
    clientId.current = null;
    setForm((prev) => ({ ...prev, [key]: value }));
  };
  const open = (kind) => {
    clientId.current = null;
    setError("");
    setModal(kind);
  };
  async function perform(action) {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await action();
      setModal(null);
      onRefresh();
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  async function submit(event) {
    event.preventDefault();
    clientId.current ??= `buyin-order-${crypto.randomUUID()}`;
    await perform(() =>
      defaultsBuyinsApi.placeOrder(organizationId, record.backendId, {
        clientRequestId: clientId.current,
        buyinRequestId: record.request.id,
        type: form.type,
        quantity: record.quantity,
        limitPrice: form.type === "LIMIT" ? Number(form.limitPrice) : null,
        costs: Number(form.costs),
      }),
    );
  }
  const receipts = record.reports.filter(
    (r) =>
      ["EXECUTED", "COMPLETED", "SETTLED"].includes(r.status) &&
      !r.inventoryReceivedAt,
  );
  return (
    <section className="db-buyin-orders" aria-label="Buy-in orders">
      <BuyinLoanCloseout
        record={record}
        organizationId={organizationId}
        onRefresh={onRefresh}
      />
      {canReportBuyin(record) && (
        <button
          className="db-button db-primary"
          onClick={() => open("order")}
          disabled={busy}
        >
          Place buy-in order
        </button>
      )}
      {!!record.buyinOrders?.length && (
        <div className="db-scroll">
          <table>
            <thead>
              <tr>
                <th>Order</th>
                <th>Type / limit</th>
                <th>Qty</th>
                <th>Fill price</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {record.buyinOrders.map((order) => (
                <tr key={order.id}>
                  <td>
                    #{order.id}
                    <small className="db-reference">
                      {order.executionReference}
                    </small>
                  </td>
                  <td>
                    {order.type}
                    {order.limitPrice != null
                      ? ` / ${rupees(order.limitPrice)}`
                      : ""}
                  </td>
                  <td>{number(order.quantity)}</td>
                  <td>
                    {rupees(order.executionPrice)}
                    <small className="db-reference">
                      {caseDate(order.filledAt)}
                    </small>
                  </td>
                  <td>
                    {order.status}
                    {order.failureReason && (
                      <small className="db-reference">
                        {order.failureReason}
                      </small>
                    )}
                  </td>
                  <td>
                    {order.status === "PENDING" && (
                      <>
                        <button
                          className="db-button"
                          disabled={busy}
                          onClick={() =>
                            perform(() =>
                              defaultsBuyinsApi.matchOrder(
                                organizationId,
                                order.id,
                              ),
                            )
                          }
                        >
                          Check order #{order.id}
                        </button>
                        <button
                          className="db-button"
                          disabled={busy}
                          onClick={() =>
                            perform(() =>
                              defaultsBuyinsApi.cancelOrder(
                                organizationId,
                                order.id,
                              ),
                            )
                          }
                        >
                          Cancel order #{order.id}
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {receipts.map((report) => (
        <button
          key={report.id}
          className="db-button"
          disabled={busy}
          onClick={() => open({ receipt: report })}
        >
          Receive securities · BI-{String(report.id).padStart(6, "0")}
        </button>
      ))}
      {error && !modal && (
        <p className="db-form-error" role="alert">
          {error}
        </p>
      )}
      {modal === "order" && (
        <DefaultsBuyinsModal
          title="Place buy-in order"
          description="Internal demo execution using fresh market quotes. A market order matches at the quoted price; a limit order waits until that price is at or below your limit. No external broker order is sent."
          onClose={busy ? null : () => setModal(null)}
        >
          <form onSubmit={submit}>
            <div className="db-dialog-summary">
              <b>
                {record.id} / {record.security}
              </b>
              <span>
                {number(record.quantity)} securities · Original loan remains
                outstanding
              </span>
            </div>
            <div className="db-form-grid">
              <label>
                Order type
                <select
                  disabled={busy}
                  value={form.type}
                  onChange={(e) => update("type", e.target.value)}
                >
                  <option value="MARKET">Market</option>
                  <option value="LIMIT">Limit</option>
                </select>
              </label>
              <label>
                Quantity
                <input type="number" value={record.quantity ?? ""} readOnly />
              </label>
              {form.type === "LIMIT" && (
                <label>
                  Limit price (₹)
                  <input
                    required
                    disabled={busy}
                    type="number"
                    min="0.000001"
                    step="0.000001"
                    value={form.limitPrice}
                    onChange={(e) => update("limitPrice", e.target.value)}
                  />
                </label>
              )}
              <label>
                Costs (₹)
                <input
                  required
                  disabled={busy}
                  type="number"
                  min="0"
                  step="0.000001"
                  value={form.costs}
                  onChange={(e) => update("costs", e.target.value)}
                />
              </label>
            </div>
            <p className="db-form-note">
              A confirmed demo fill includes simulated securities receipt and
              credits purchased inventory once. The purchase amount is recorded
              as payable; collateral and rebates remain unchanged.
            </p>
            {error && (
              <p className="db-form-error" role="alert">
                {error}
              </p>
            )}
            <footer>
              <button
                className="db-button"
                type="button"
                disabled={busy}
                onClick={() => setModal(null)}
              >
                Cancel
              </button>
              <button className="db-button db-primary" disabled={busy}>
                {busy ? "Submitting…" : "Place demo order"}
              </button>
            </footer>
          </form>
        </DefaultsBuyinsModal>
      )}
      {modal?.receipt && (
        <DefaultsBuyinsModal
          title="Confirm securities receipt"
          description="Confirm that the purchased securities have been received. This adds owned inventory and records the purchase payable once."
          onClose={busy ? null : () => setModal(null)}
        >
          <div className="db-dialog-summary">
            <b>
              {number(modal.receipt.quantity)} {record.security}
            </b>
            <span>
              {modal.receipt.executionReference ||
                `Buy-in #${modal.receipt.id}`}
            </span>
          </div>
          <p className="db-form-note">
            This records purchased stock. It does not mark the borrower’s
            original securities as returned.
          </p>
          {error && (
            <p className="db-form-error" role="alert">
              {error}
            </p>
          )}
          <footer>
            <button
              className="db-button"
              disabled={busy}
              onClick={() => setModal(null)}
            >
              Cancel
            </button>
            <button
              className="db-button db-primary"
              disabled={busy}
              onClick={() =>
                perform(() =>
                  defaultsBuyinsApi.receiveInventory(
                    organizationId,
                    modal.receipt.id,
                    `buyin-receipt-${modal.receipt.id}`,
                  ),
                )
              }
            >
              {busy ? "Receiving…" : "Confirm receipt"}
            </button>
          </footer>
        </DefaultsBuyinsModal>
      )}
    </section>
  );
}
