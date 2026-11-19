import CollateralSalePanel from "./CollateralSalePanel";
import BuyinLoanCloseout from "./BuyinLoanCloseout";
import { useRef, useState } from "react";
import { canReportBuyin, defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import { number, rupees, caseDate } from "../data/defaultsBuyinsData";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function BuyinOrderPanel({
  record,
  organizationId,
  onRefresh,
  onReport,
}) {
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
  const price =
    form.type === "LIMIT"
      ? Number(form.limitPrice)
      : Number(record.marketPrice);
  const estimatedValue =
    Number.isFinite(price) && price > 0
      ? price * Number(record.quantity)
      : null;
  const fees = Number(form.costs);
  const estimatedTotal =
    estimatedValue != null && Number.isFinite(fees) && fees >= 0
      ? estimatedValue + fees
      : null;
  const internalFill = (record.buyinOrders || []).some(
    (order) => order.status === "FILLED",
  );
  const receipts = record.reports.filter(
    (r) =>
      ["EXECUTED", "COMPLETED", "SETTLED"].includes(r.status) &&
      !r.inventoryReceivedAt,
  );
  return (
    <section className="db-buyin-orders" aria-label="Buy-in orders">
      <CollateralSalePanel
        record={record}
        organizationId={organizationId}
        onRefresh={onRefresh}
      />
      <div className="db-buyin-actions">
        <BuyinLoanCloseout
          record={record}
          organizationId={organizationId}
          onRefresh={onRefresh}
          recordExecution={internalFill}
        />
        {canReportBuyin(record) && !record.collateralFunding?.needsSale && (
          <>
            <button
              className="db-button db-primary"
              onClick={() => open("order")}
              disabled={busy}
            >
              Place buy-in order
            </button>
            <button
              className="db-button"
              onClick={onReport}
              disabled={busy}
              title="Record a trade executed outside this system"
            >
              Record external execution
            </button>
          </>
        )}
      </div>
      {canReportBuyin(record) && !record.collateralFunding?.needsSale && (
        <p className="db-footnote">
          Place an order here, or record an external trade. Once execution is
          confirmed, record settlement to close the loan.
        </p>
      )}
      {internalFill && record.loanStatus !== "CLOSED" && (
        <p className="db-footnote">
          Order executed and securities received. Select Record execution &amp;
          close loan to review collateral settlement and confirm closure; the
          trade will not be recorded twice.
        </p>
      )}
      {!!record.buyinOrders?.length && (
        <div className="db-buyin-order-list">
          {record.buyinOrders.map((order) => (
            <article
              className="db-buyin-order"
              key={order.id}
              aria-label={`Order #${order.id}`}
            >
              <div className="db-buyin-order-heading">
                <b>Order #{order.id}</b>
                <span>{order.status}</span>
              </div>
              <dl className="db-buyin-order-facts">
                <div>
                  <dt>Order type</dt>
                  <dd>{order.type}</dd>
                </div>
                <div>
                  <dt>Quantity</dt>
                  <dd>{number(order.quantity)}</dd>
                </div>
                {order.limitPrice != null && (
                  <div>
                    <dt>Limit price</dt>
                    <dd>{rupees(order.limitPrice)}</dd>
                  </div>
                )}
                <div>
                  <dt>Execution price</dt>
                  <dd>{rupees(order.executionPrice)}</dd>
                </div>
              </dl>
              {order.failureReason && (
                <p className="db-buyin-order-message">{order.failureReason}</p>
              )}
              {order.status === "EXECUTION_PENDING" && (
                <p className="db-buyin-order-message">
                  Price matched. Awaiting execution confirmation.
                </p>
              )}
              {order.status === "PENDING" &&
                order.type === "LIMIT" &&
                !order.failureReason && (
                  <p className="db-buyin-order-message">
                    Waiting for the market price to reach your limit.
                  </p>
                )}
              {order.quoteAt && (
                <p className="db-buyin-order-reference">
                  Price quoted: {caseDate(order.quoteAt)}
                </p>
              )}
              {order.filledAt && (
                <p className="db-buyin-order-reference">
                  Executed: {caseDate(order.filledAt)}
                </p>
              )}
              {order.executionReference && (
                <p className="db-buyin-order-reference">
                  Trade reference: {order.executionReference}
                </p>
              )}
              {order.status === "PENDING" && (
                <div className="db-buyin-actions">
                  <button
                    className="db-button"
                    disabled={busy}
                    onClick={() =>
                      perform(() =>
                        defaultsBuyinsApi.matchOrder(organizationId, order.id),
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
                        defaultsBuyinsApi.cancelOrder(organizationId, order.id),
                      )
                    }
                  >
                    Cancel order #{order.id}
                  </button>
                </div>
              )}
            </article>
          ))}
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
          description="Internal demo execution using the latest available market price, including outside market hours. Quote age does not prevent execution. A market order matches at the quoted price; a limit order waits until that price is at or below your limit. No external broker order is sent."
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
                Additional execution fees (₹)
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
            <dl
              className="db-buyin-purchase-estimate"
              aria-label="Buy-in purchase estimate"
            >
              <div>
                <dt>
                  {form.type === "LIMIT"
                    ? "Limit price"
                    : "Last quoted market price"}
                </dt>
                <dd>{price > 0 ? rupees(price) : "Awaiting quote"}</dd>
              </div>
              <div>
                <dt>
                  {form.type === "LIMIT"
                    ? "Maximum securities value at limit"
                    : "Estimated securities value"}
                </dt>
                <dd>
                  {estimatedValue != null
                    ? rupees(estimatedValue)
                    : "Awaiting quote"}
                </dd>
              </div>
              <div>
                <dt>Additional execution fees</dt>
                <dd>{fees >= 0 ? rupees(fees) : "Enter valid fees"}</dd>
              </div>
              <div>
                <dt>
                  {form.type === "LIMIT"
                    ? "Maximum total at limit"
                    : "Estimated total purchase amount"}
                </dt>
                <dd>
                  {estimatedTotal != null
                    ? rupees(estimatedTotal)
                    : "Awaiting quote"}
                </dd>
              </div>
            </dl>
            {record.collateralFunding && (
              <p className="db-form-note">
                Available cash collateral:{" "}
                {rupees(record.collateralFunding.cashHeld)}.{" "}
                {estimatedTotal != null &&
                estimatedTotal > Number(record.collateralFunding.cashHeld)
                  ? "Additional cash collateral is required for this order."
                  : "The purchase and execution fees will be settled from this collateral."}
              </p>
            )}
            <p className="db-form-note">
              Execution fees are optional and may be ₹0. Securities are
              purchased at the execution price; the final total is quantity ×
              execution price + fees. This estimate is not a guaranteed fill
              price.
            </p>
            <p className="db-form-note">
              A confirmed demo fill includes simulated securities receipt and
              credits purchased inventory once. The purchase amount is recorded
              as payable. After execution, confirm Record execution & close loan
              to settle costs from collateral and close the eligible loan.
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
              <button
                className="db-button db-primary"
                disabled={
                  busy ||
                  record.collateralFunding?.needsSale ||
                  (estimatedTotal != null &&
                    record.collateralFunding?.cashHeld != null &&
                    estimatedTotal > Number(record.collateralFunding.cashHeld))
                }
              >
                {busy ? "Submitting…" : "Place order"}
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
