import { useRef, useState } from "react";
import { defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import { number, rupees } from "../data/defaultsBuyinsData";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function LiveBuyinRequestDialog({
  record,
  organizationId,
  onClose,
  onSubmitted,
}) {
  const requestId = useRef(`lenddesk-buyin-${crypto.randomUUID()}`);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const submitting = useRef(false);
  async function submit(event) {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    setBusy(true);
    setError("");
    try {
      const request = await defaultsBuyinsApi.request(
        organizationId,
        record.backendId,
        requestId.current,
      );
      onSubmitted(request);
    } catch (e) {
      setError(e.message);
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  }
  return (
    <DefaultsBuyinsModal
      title="Request buy-in"
      onClose={busy ? null : onClose}
      description="Submit a buy-in request for the lifecycle owner to process. Acceptance queues the request; it does not confirm execution or settlement."
    >
      <form onSubmit={submit}>
        <div className="db-dialog-summary">
          <b>
            {record.id} / {record.security}
          </b>
          <span>
            {record.borrower} · {record.loanId}
          </span>
        </div>
        <dl className="db-live-confirm">
          <div>
            <dt>Source obligation</dt>
            <dd>{record.reference}</dd>
          </div>
          <div>
            <dt>Reported quantity</dt>
            <dd>{number(record.quantity)}</dd>
          </div>
          <div>
            <dt>Reported exposure</dt>
            <dd>{rupees(record.exposure)}</dd>
          </div>
          <div>
            <dt>Default state</dt>
            <dd>{record.rawStatus}</dd>
          </div>
        </dl>
        <p className="db-form-note">
          The current backend accepts a request reference only. The lifecycle
          owner determines the executable quantity, price and settlement. No
          exchange limit order is placed by this form.
        </p>
        {error && (
          <p role="alert" className="db-form-error">
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
            {busy ? "Submitting…" : "Submit buy-in request"}
          </button>
        </footer>
      </form>
    </DefaultsBuyinsModal>
  );
}
