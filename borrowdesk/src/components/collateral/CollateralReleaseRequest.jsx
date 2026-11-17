import { useEffect, useRef, useState } from "react";
import { collateralApi } from "../../api/collateralApi";
import "./CollateralReleaseRequest.css";
import CollateralReleaseStatus from "./CollateralReleaseStatus";

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
  const [assetType, setAssetType] = useState("CASH");
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
    setAssetType("CASH");
    setAmount("");
    let checking = false;
    async function refreshPreview() {
      if (checking || document.hidden) return;
      checking = true;
      try {
        const result = await collateralApi.releasePreview(loanId);
        if (active) setPreview(result);
      } catch (e) {
        if (active) setError(e.message);
      } finally {
        checking = false;
      }
    }
    refreshPreview();
    const timer = setInterval(refreshPreview, 5000);
    window.addEventListener("focus", refreshPreview);
    return () => {
      active = false;
      clearInterval(timer);
      window.removeEventListener("focus", refreshPreview);
    };
  }, [open, loanId]);
  const positions = (preview?.positions ?? []).map((p) => ({
    ...p,
    asset_type: p.asset_type ?? p.assetType,
    security_id: p.security_id ?? p.securityId,
    cash_amount: p.cash_amount ?? p.cashAmount,
    requested_release_cash: p.requested_release_cash ?? p.requestedReleaseCash,
    requested_release_quantity:
      p.requested_release_quantity ?? p.requestedReleaseQuantity,
  }));
  const lots = positions.filter(
    (p) => p.status === "POSTED" && p.asset_type === assetType,
  );
  const selected =
    lots.find((p) => String(p.id) === positionId) ??
    (lots.length === 1 ? lots[0] : null);
  const cash = assetType === "CASH";
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
    if (
      !selected ||
      busy ||
      !(Number(amount) > 0) ||
      Number(amount) > max ||
      (!cash && !Number.isSafeInteger(Number(amount)))
    )
      return;
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
                <CollateralReleaseStatus positions={positions} />
                <label>
                  Collateral type
                  <select
                    aria-label="Collateral type"
                    value={assetType}
                    disabled={busy}
                    onChange={(e) => {
                      setAssetType(e.target.value);
                      setPositionId("");
                      setAmount("");
                    }}
                  >
                    <option value="CASH">Cash</option>
                    <option value="SECURITY">Security</option>
                  </select>
                </label>
                <label>
                  {cash ? "Cash collateral lot" : "Security collateral"}
                  <select
                    aria-label={cash ? "Cash collateral lot" : "Security collateral"}
                    required
                    disabled={busy || !lots.length}
                    value={selected ? String(selected.id) : ""}
                    onChange={(e) => {
                      setPositionId(e.target.value);
                      setAmount("");
                    }}
                  >
                    <option value="">
                      {cash ? "Select cash collateral" : "Select security"}
                    </option>
                    {lots.map((p) => (
                      <option key={p.id} value={p.id}>
                        {cash
                          ? `Lot ${p.id} · ₹${number(p.cash_amount)} held`
                          : `${p.securitySymbol ?? p.security_symbol ?? `Security ${p.security_id}`} ${p.exchange ?? ""} · Lot ${p.id} · ${number(p.quantity)} units held`}
                      </option>
                    ))}
                  </select>
                </label>
                {!lots.length && (
                  <p>
                    No posted {cash ? "cash" : "security"} collateral is
                    available for this loan. Pending releases must be decided
                    before that lot can be requested again.
                  </p>
                )}
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
                    {max <= 0 && (
                      <p className="release-error">
                        {cash ? "No cash amount" : "No whole security units"}{" "}
                        can currently be released from this lot without reducing
                        required coverage. Other pending releases may be using
                        the excess.
                      </p>
                    )}
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
                  Number(amount) > max ||
                  (!cash && !Number.isSafeInteger(Number(amount)))
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
