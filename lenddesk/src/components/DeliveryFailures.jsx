import { useEffect, useRef, useState } from "react";
import "./DeliveryFailures.css";

export default function DeliveryFailures({
  enabled = true,
  organizationId,
  get,
  post,
  onChanged,
}) {
  const [rows, setRows] = useState([]),
    [open, setOpen] = useState(false),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState("");
  const [revision, setRevision] = useState(0),
    [decision, setDecision] = useState(null);
  const dialog = useRef(null),
    sending = useRef(false);
  const base = `/api/v1/organizations/${organizationId}/delivery-failures`;
  useEffect(() => {
    if (!enabled) return;
    let live = true,
      timer;
    async function load() {
      try {
        const response = await get(base);
        if (live) {
          setRows(Array.isArray(response) ? response : []);
          setError("");
        }
      } catch (e) {
        if (live) setError(e.message || "Failure notifications unavailable");
      }
      if (live) timer = setTimeout(load, 10000);
    }
    load();
    return () => {
      live = false;
      clearTimeout(timer);
    };
  }, [enabled, base, get, revision]);
  if (!enabled) return null;
  function review(row, action) {
    setDecision({ row, action });
    setError("");
    dialog.current.showModal();
  }
  async function submit() {
    if (sending.current || !decision) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await post(
        `${base}/${encodeURIComponent(decision.row.id)}/${decision.action}`,
        {},
      );
      dialog.current.close();
      setDecision(null);
      setRevision((v) => v + 1);
      onChanged?.();
      setNotice(
        decision.action === "retry"
          ? "Retry queued using the original event ID."
          : "Delivery closed. Check the current business state before creating another request.",
      );
    } catch (e) {
      setError(e.message || "Unable to update delivery");
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  const failed = rows.filter((r) => r.status === "FAILED");
  return (
    <aside className="delivery-failures" aria-label="Delivery notifications">
      <button
        className="delivery-trigger"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        aria-label={`Failed deliveries (${failed.length})`}
        title="Failed deliveries"
      >
        ⚠ <span>{failed.length}</span>
      </button>
      {open && (
        <section className="delivery-popover">
          <header>
            <h2>Failed deliveries</h2>
            <button onClick={() => setRevision((v) => v + 1)} disabled={busy}>
              Refresh
            </button>
            <button
              onClick={() => setOpen(false)}
              aria-label="Close delivery notifications"
            >
              ×
            </button>
          </header>
          <p>
            Automatic retries stop after the configured limit. Delivery failures
            are separate from business rejections.
          </p>
          {error && (
            <p role="alert" className="delivery-error">
              {error}
            </p>
          )}
          {notice && <p role="status">{notice}</p>}
          {!rows.length && !error && <p>No failed or retrying deliveries.</p>}
          <ul>
            {rows.map((row) => (
              <li key={row.id}>
                <strong>
                  {row.eventType.replaceAll("_", " ")} · #{row.referenceId}
                </strong>
                <span className="delivery-state">{row.status}</span>
                <small>
                  {row.source} / {row.stage} · {row.attempts} attempt(s)
                </small>
                <p>{row.lastError || "Delivery attempts exhausted"}</p>
                <code title={row.eventId}>{row.eventId}</code>
                <footer>
                  <button
                    disabled={busy || row.status !== "FAILED"}
                    onClick={() => review(row, "retry")}
                  >
                    Retry
                  </button>
                  <button
                    disabled={busy || row.status !== "FAILED"}
                    onClick={() => review(row, "close")}
                  >
                    Close delivery
                  </button>
                </footer>
              </li>
            ))}
          </ul>
        </section>
      )}
      <dialog
        ref={dialog}
        className="delivery-dialog"
        onCancel={(e) => {
          if (busy) e.preventDefault();
        }}
      >
        <h2>
          {decision?.action === "retry"
            ? "Retry delivery"
            : "Close failed delivery"}
        </h2>
        <p>
          {decision?.row.eventType.replaceAll("_", " ")} · #
          {decision?.row.referenceId}
        </p>
        <p>
          {decision?.action === "retry"
            ? "The original event ID and payload will be reused so a duplicate delivery cannot create a second business request."
            : "Stops further delivery attempts and retains the failed record for audit. This does not cancel or reverse a loan, cash block, or settlement. A timeout can occur after the receiver accepted the request: verify its current state before making a replacement."}
        </p>
        {error && (
          <p role="alert" className="delivery-error">
            {error}
          </p>
        )}
        <footer>
          <button disabled={busy} onClick={() => dialog.current.close()}>
            Back
          </button>
          <button disabled={busy} onClick={submit}>
            {busy
              ? "Submitting…"
              : decision?.action === "retry"
                ? "Confirm retry"
                : "Close delivery"}
          </button>
        </footer>
      </dialog>
    </aside>
  );
}
