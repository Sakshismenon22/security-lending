import { useEffect, useRef, useState } from "react";
import { lenderGet, lenderPost } from "../api/lendDeskApi";
import "./AdminInventoryReceipt.css";
export default function AdminInventoryReceipt({ organizationId, onSaved }) {
  const [admin, setAdmin] = useState(false),
    [open, setOpen] = useState(false),
    [securities, setSecurities] = useState([]),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(false),
    [busy, setBusy] = useState(false);
  const [form, setForm] = useState({
    securityId: "",
    quantity: "",
    reference: "",
  });
  const sending = useRef(false);
  useEffect(() => {
    let live = true;
    lenderGet(`/api/v1/organizations/${organizationId}/admin-access`)
      .then((a) => {
        if (live) setAdmin(a.admin === true);
      })
      .catch(() => {
        if (live) setAdmin(false);
      });
    return () => {
      live = false;
    };
  }, [organizationId]);
  useEffect(() => {
    if (!open) return;
    let live = true;
    lenderGet("/api/v1/securities")
      .then((data) => {
        if (live)
          setSecurities(Array.isArray(data) ? data : data.content || []);
      })
      .catch((e) => {
        if (live) setError(e.message);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [open]);
  const update = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));
  async function submit(e) {
    e.preventDefault();
    if (sending.current) return;
    const quantity = Number(form.quantity);
    if (!Number.isSafeInteger(quantity) || quantity <= 0) {
      setError("Enter a positive whole-number quantity.");
      return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await lenderPost(
        `/api/v1/organizations/${organizationId}/inventory/receipts`,
        {
          securityId: Number(form.securityId),
          quantity,
          reference: form.reference.trim(),
        },
      );
      setOpen(false);
      onSaved();
    } catch (failure) {
      setError(failure.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  if (!admin) return null;
  return (
    <>
      <button
        className="admin-inventory-add"
        onClick={() => {
          setError("");
          setLoading(true);
          setSecurities([]);
          setForm({
            securityId: "",
            quantity: "",
            reference: `CUSTODY-${crypto.randomUUID()}`,
          });
          setOpen(true);
        }}
      >
        Add securities
      </button>
      {open && (
        <div className="admin-inventory-backdrop">
          <form
            className="admin-inventory-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="admin-inventory-title"
            onSubmit={submit}
          >
            <header>
              <h2 id="admin-inventory-title">Add securities to inventory</h2>
              <button
                type="button"
                disabled={busy}
                aria-label="Close inventory receipt"
                onClick={() => setOpen(false)}
              >
                ×
              </button>
            </header>
            <p>
              Record securities received into custody. Existing positions
              increase by this quantity; otherwise a new position is created.
            </p>
            <label>
              Security
              <select
                required
                disabled={busy || loading}
                value={form.securityId}
                onChange={(e) => update("securityId", e.target.value)}
              >
                <option value="">
                  {loading ? "Loading securities…" : "Select security"}
                </option>
                {securities.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.symbol} · {s.name || s.securityName || s.id}
                  </option>
                ))}
              </select>
            </label>
            {!loading && !securities.length && (
              <p role="status">
                No securities are registered. Import the security catalogue
                before recording a receipt.
              </p>
            )}
            <label>
              Quantity received
              <input
                type="number"
                required
                min="1"
                step="1"
                disabled={busy}
                value={form.quantity}
                onChange={(e) => update("quantity", e.target.value)}
              />
            </label>
            <label>
              Custody receipt reference
              <input
                required
                maxLength={160}
                disabled={busy}
                value={form.reference}
                onChange={(e) => update("reference", e.target.value)}
              />
            </label>
            <p>
              Reserved and loaned quantities remain unchanged. Reuse the same
              reference when retrying this receipt.
            </p>
            {error && (
              <p className="admin-inventory-error" role="alert">
                {error}
              </p>
            )}
            <footer>
              <button
                type="button"
                disabled={busy}
                onClick={() => setOpen(false)}
              >
                Cancel
              </button>
              <button
                className="primary"
                disabled={busy || loading || !securities.length}
              >
                {busy ? "Saving…" : "Confirm securities received"}
              </button>
            </footer>
          </form>
        </div>
      )}
    </>
  );
}
