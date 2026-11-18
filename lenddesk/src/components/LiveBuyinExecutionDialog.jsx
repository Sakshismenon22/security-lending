import { useRef, useState } from "react";
import { defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function LiveBuyinExecutionDialog({
  record,
  organizationId,
  onClose,
  onSubmitted,
}) {
  const requestId = useRef(null);
  const sending = useRef(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [form, setForm] = useState({
    price: "",
    costs: "0",
    executionReference: "",
  });
  const update = (key, value) => {
    requestId.current = null;
    setForm((previous) => ({ ...previous, [key]: value }));
  };
  async function submit(event) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      requestId.current ??= `lenddesk-execution-${crypto.randomUUID()}`;
      const result = await defaultsBuyinsApi.report(
        organizationId,
        record.backendId,
        {
          clientRequestId: requestId.current,
          buyinRequestId: record.request.id,
          quantity: record.quantity,
          price: Number(form.price),
          costs: Number(form.costs),
          executionReference: form.executionReference.trim(),
        },
      );
      onSubmitted(result);
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <DefaultsBuyinsModal
      title="Record buy-in execution"
      description="Record an already executed trade. Lifecycle verifies the outstanding obligation before publishing the execution report. This does not settle cash or collateral."
      onClose={busy ? null : onClose}
    >
      <form onSubmit={submit}>
        <div className="db-dialog-summary">
          <b>
            {record.id} / {record.security}
          </b>
          <span>
            {record.quantity} securities · {record.borrower}
          </span>
        </div>
        <div className="db-form-grid">
          <label>
            Executed quantity
            <input type="number" value={record.quantity ?? ""} readOnly />
          </label>
          <label>
            Execution price (₹)
            <input
              type="number"
              min="0.000001"
              step="0.000001"
              required
              disabled={busy}
              value={form.price}
              onChange={(e) => update("price", e.target.value)}
            />
          </label>
          <label>
            Execution costs (₹)
            <input
              type="number"
              min="0"
              step="0.000001"
              required
              disabled={busy}
              value={form.costs}
              onChange={(e) => update("costs", e.target.value)}
            />
          </label>
          <label>
            Trade confirmation reference
            <input
              required
              maxLength={120}
              disabled={busy}
              value={form.executionReference}
              onChange={(e) => update("executionReference", e.target.value)}
            />
          </label>
        </div>
        <p className="db-form-note">
          The confirmation must cover the full current quantity. A stale
          quantity is rejected; refresh the case before trying again.
        </p>
        {error && (
          <p className="db-form-error" role="alert">
            {error}
          </p>
        )}
        <footer>
          <button
            type="button"
            className="db-button"
            onClick={onClose}
            disabled={busy}
          >
            Cancel
          </button>
          <button className="db-button db-primary" disabled={busy}>
            {busy ? "Submitting…" : "Submit execution report"}
          </button>
        </footer>
      </form>
    </DefaultsBuyinsModal>
  );
}
