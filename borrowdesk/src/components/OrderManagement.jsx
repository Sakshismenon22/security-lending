import { useRef, useState } from "react";
import { Pencil, Trash2, X } from "lucide-react";
import { tradingDeskApi } from "../api/tradingDeskApi";

export const isUnfilledOrder = (order, trades = []) =>
  order.status === "PENDING" &&
  !trades.some(
    (trade) => String(trade.orderId ?? trade.order_id) === String(order.id),
  );

export default function OrderManagement({
  orders,
  trades,
  securities,
  onChanged,
  children,
}) {
  const dialog = useRef(null),
    inFlight = useRef(false);
  const [selection, setSelection] = useState(null),
    [quantity, setQuantity] = useState(""),
    [type, setType] = useState("LIMIT"),
    [price, setPrice] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const symbol = (order) =>
    order.symbol ??
    securities.find(
      (s) => String(s.id) === String(order.securityId ?? order.security_id),
    )?.symbol ??
    "—";
  function open(order, mode) {
    setSelection({ order, mode });
    setQuantity(String(order.quantity));
    setType(order.type);
    setPrice(String(order.limitPrice ?? order.limit_price ?? ""));
    setError("");
    setNotice("");
    dialog.current.showModal();
  }
  const actions = (order) =>
    isUnfilledOrder(order, trades) ? (
      <span className="order-row-actions">
        <button
          type="button"
          title={`Edit order ${order.id}`}
          aria-label={`Edit order ${order.id}`}
          disabled={busy}
          onClick={() => open(order, "edit")}
        >
          <Pencil size={13} />
        </button>
        <button
          type="button"
          className="order-cancel-action"
          title={`Cancel order ${order.id}`}
          aria-label={`Cancel order ${order.id}`}
          disabled={busy}
          onClick={() => open(order, "cancel")}
        >
          <Trash2 size={13} />
        </button>
      </span>
    ) : (
      <span title="Only unfilled pending orders can be changed">—</span>
    );
  async function submit(event) {
    event.preventDefault();
    if (inFlight.current || !selection) return;
    const { order, mode } = selection;
    if (
      mode === "edit" &&
      (!Number.isSafeInteger(Number(quantity)) ||
        Number(quantity) <= 0 ||
        (type === "LIMIT" && !(Number(price) > 0)))
    ) {
      setError("Enter a positive whole quantity and a positive limit price.");
      return;
    }
    inFlight.current = true;
    setBusy(true);
    setError("");
    try {
      const result =
        mode === "cancel"
          ? await tradingDeskApi.cancelOrder(order.id)
          : await tradingDeskApi.updateOrder(order.id, {
              quantity: Number(quantity),
              type,
              limitPrice: type === "LIMIT" ? Number(price) : null,
            });
      dialog.current.close();
      setNotice(
        `Order ${order.id} ${mode === "cancel" ? "cancelled" : "updated"}${result?.status ? ` · ${result.status}` : ""}.`,
      );
      await onChanged?.();
    } catch (e) {
      setError(
        `${e.message || "Unable to change order"}. Refresh to check its latest status before retrying.`,
      );
    } finally {
      inFlight.current = false;
      setBusy(false);
    }
  }
  const pending = orders.filter((order) => isUnfilledOrder(order, trades));
  return (
    <>
      {children({ actions, pending, symbol })}
      {notice && (
        <p className="order-management-notice" role="status">
          {notice}
        </p>
      )}
      <dialog
        ref={dialog}
        className="order-management-dialog"
        onCancel={(e) => {
          if (inFlight.current) e.preventDefault();
        }}
      >
        <form onSubmit={submit}>
          <header>
            <h2>
              {selection?.mode === "cancel"
                ? "Cancel unfilled order"
                : "Update unfilled order"}
            </h2>
            <button
              type="button"
              aria-label="Close order dialog"
              disabled={busy}
              onClick={() => dialog.current.close()}
            >
              <X size={16} />
            </button>
          </header>
          {selection && (
            <p>
              Order #{selection.order.id} · {symbol(selection.order)} ·{" "}
              {selection.order.side} · {selection.order.source}
            </p>
          )}
          {selection?.mode === "edit" ? (
            <>
              <label>
                Quantity (shares)
                <input
                  required
                  type="number"
                  min="1"
                  step="1"
                  value={quantity}
                  disabled={busy}
                  onChange={(e) => setQuantity(e.target.value)}
                />
              </label>
              <label>
                Order type
                <select
                  value={type}
                  disabled={busy}
                  onChange={(e) => setType(e.target.value)}
                >
                  <option value="LIMIT">LIMIT</option>
                  <option value="MARKET">MARKET</option>
                </select>
              </label>
              {type === "LIMIT" && (
                <label>
                  Limit price (₹)
                  <input
                    required
                    type="number"
                    min="0.000001"
                    step="0.000001"
                    value={price}
                    disabled={busy}
                    onChange={(e) => setPrice(e.target.value)}
                  />
                </label>
              )}
              <p>
                Cash or shares will be reserved for the updated quantity. An
                executable order may fill immediately.
              </p>
            </>
          ) : (
            <p>
              Cancel the entire unfilled order and release its reserved cash or
              shares?
            </p>
          )}
          {error && (
            <p role="alert" className="order-management-error">
              {error}
            </p>
          )}
          <footer>
            <button
              type="button"
              disabled={busy}
              onClick={() => dialog.current.close()}
            >
              Back
            </button>
            <button className="order-management-confirm" disabled={busy}>
              {busy
                ? "Submitting…"
                : selection?.mode === "cancel"
                  ? "Confirm cancellation"
                  : "Update order"}
            </button>
          </footer>
        </form>
      </dialog>
    </>
  );
}
