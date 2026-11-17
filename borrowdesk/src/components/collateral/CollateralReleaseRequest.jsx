import { useEffect, useRef, useState } from "react";
import { collateralApi } from "../../api/collateralApi";
import "./CollateralReleaseRequest.css";

const number = (value) =>
  Number(value ?? 0).toLocaleString("en-IN", { maximumFractionDigits: 6 });

export default function CollateralReleaseRequest({
  loanId,
  disabled = false,
  onComplete,
}) {
  const dialog = useRef(null);
  const [open, setOpen] = useState(false);
  const [preview, setPreview] = useState(null);
  const [positionId, setPositionId] = useState("");
  const [amount, setAmount] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!open) return;
    let active = true;
    dialog.current.showModal();
    setPreview(null);
    setError("");
    setPositionId("");
    setAmount("");
    collateralApi
      .releasePreview(loanId)
      .then((result) => {
        if (active) setPreview(result);
      })
      .catch((e) => {
        if (active) setError(e.message);
      });
    return () => {
      active = false;
    };
  }, [open, loanId]);
  const positions = preview?.positions ?? [];
  const selected = positions.find((p) => String(p.id) === positionId);
  const cash = selected?.asset_type === "CASH";
  const max = selected
    ? Number(cash ? selected.maxCashAmount : selected.maxQuantity)
    : 0;
  function close() {
    if (busy) return;
    dialog.current?.close();
    setOpen(false);
  }
  async function submit(e) {
    e.preventDefault();
    if (!selected || busy) return;
    setBusy(true);
    setError("");
    try {
      await collateralApi.release(
        selected.id,
        cash ? { cashAmount: amount } : { quantity: Number(amount) },
      );
      dialog.current.close();
      setOpen(false);
      onComplete?.("Collateral release requested. Awaiting lender approval.");
    } catch (e) {
      setError(e.message);
      // Prices and other pending requests can change after opening the form.
      try {
        setPreview(await collateralApi.releasePreview(loanId));
      } catch {
        /* retain original error */
      }
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <button
        type="button"
        className="collateral-release-trigger"
        disabled={disabled}
        onClick={() => setOpen(true)}
      >
        Request collateral release
      </button>
      {open && (
        <dialog
          ref={dialog}
          className="collateral-release-dialog"
          onCancel={(e) => {
            e.preventDefault();
            close();
          }}
        >
          <form onSubmit={submit}>
            <header>
              <h2>Request collateral release</h2>
              <button
                type="button"
                aria-label="Close release request"
                disabled={busy}
                onClick={close}
              >
                ×
              </button>
            </header>
            <p>
              Loan {loanId}. Release only the excess above required
              haircut-adjusted coverage. The lender checks coverage again before
              accepting.
            </p>
            {!preview && !error && (
              <p role="status">Checking available excess…</p>
            )}
            {error && (
              <p role="alert" className="release-error">
                {error}
              </p>
            )}
            {preview && (
              <>
                <dl>
                  <div>
                    <dt>Required collateral</dt>
                    <dd>₹{number(preview.requiredCollateral)}</dd>
                  </div>
                  <div>
                    <dt>Recognized collateral</dt>
                    <dd>₹{number(preview.recognizedValue)}</dd>
                  </div>
                  <div>
                    <dt>Pending releases</dt>
                    <dd>₹{number(preview.pendingReleaseValue)}</dd>
                  </div>
                  <div>
                    <dt>Available excess</dt>
                    <dd>₹{number(preview.availableExcess)}</dd>
                  </div>
                </dl>
                {positions
                  .filter((p) => p.status === "RELEASE_REQUESTED")
                  .map((p) => (
                    <p key={p.id}>
                      Lot {p.id}: release pending —{" "}
                      {p.asset_type === "CASH"
                        ? `₹${number(p.requested_release_cash ?? p.cash_amount)}`
                        : `${number(p.requested_release_quantity ?? p.quantity)} units`}
                    </p>
                  ))}
                <label>
                  Collateral lot
                  <select
                    required
                    disabled={busy}
                    value={positionId}
                    onChange={(e) => {
                      setPositionId(e.target.value);
                      setAmount("");
                    }}
                  >
                    <option value="">Select collateral</option>
                    {positions
                      .filter((p) => p.status === "POSTED")
                      .map((p) => (
                        <option
                          key={p.id}
                          value={p.id}
                          disabled={
                            Number(
                              p.asset_type === "CASH"
                                ? p.maxCashAmount
                                : p.maxQuantity,
                            ) <= 0
                          }
                        >
                          Lot {p.id} ·{" "}
                          {p.asset_type === "CASH"
                            ? `Cash ₹${number(p.cash_amount)}`
                            : `Security ${p.security_id} · ${number(p.quantity)} units`}
                        </option>
                      ))}
                  </select>
                </label>
                {selected && (
                  <>
                    <label>
                      {cash
                        ? "Cash amount to release (₹)"
                        : "Security units to release"}
                      <input
                        required
                        disabled={busy}
                        type="number"
                        min={cash ? "0.000001" : "1"}
                        step={cash ? "0.000001" : "1"}
                        max={max}
                        value={amount}
                        onChange={(e) => setAmount(e.target.value)}
                      />
                    </label>
                    <p>
                      Maximum for this lot: {cash ? "₹" : ""}
                      {number(max)}
                      {cash ? "" : " whole units"}, after haircut and pending
                      releases.
                    </p>
                  </>
                )}
                {Number(preview.availableExcess) <= 0 && (
                  <p>
                    No excess collateral is currently available for release.
                  </p>
                )}
              </>
            )}
            <footer>
              <button type="button" disabled={busy} onClick={close}>
                Cancel
              </button>
              <button
                type="submit"
                disabled={
                  busy ||
                  !selected ||
                  !(Number(amount) > 0) ||
                  Number(amount) > max
                }
              >
                {busy ? "Sending…" : "Send release request"}
              </button>
            </footer>
          </form>
        </dialog>
      )}
    </>
  );
}
