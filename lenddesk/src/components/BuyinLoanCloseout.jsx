import { useRef, useState } from "react";
import { defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import { number, rupees, caseDate } from "../data/defaultsBuyinsData";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function BuyinLoanCloseout({
  record,
  organizationId,
  onRefresh,
}) {
  const [open, setOpen] = useState(false),
    [preview, setPreview] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const [reference, setReference] = useState("");
  const sending = useRef(false);
  async function load() {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      setPreview(
        await defaultsBuyinsApi.closeoutPreview(
          organizationId,
          record.backendId,
        ),
      );
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  function show() {
    setOpen(true);
    setPreview(null);
    setReference(`close-buyin-${crypto.randomUUID()}`);
    load();
  }
  async function submit(event) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await defaultsBuyinsApi.closeLoan(
        organizationId,
        record.backendId,
        reference.trim(),
      );
      setOpen(false);
      onRefresh();
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  if (
    !record.reports.some((r) => r.inventoryReceivedAt) &&
    record.loanStatus !== "CLOSED"
  )
    return null;
  return (
    <>
      <button className="db-button db-primary" onClick={show}>
        {record.loanStatus === "CLOSED"
          ? "View loan closure"
          : "Close loan after buy-in"}
      </button>
      {open && (
        <DefaultsBuyinsModal
          title="Loan closure after buy-in"
          description="Close the remaining securities loan using received buy-in stock, apply cash collateral to purchase costs, and release the surplus."
          onClose={busy ? null : () => setOpen(false)}
        >
          <form onSubmit={submit}>
            {busy && !preview && (
              <p role="status">Checking loan closure eligibility…</p>
            )}
            {preview && (
              <>
                <dl className="db-live-confirm">
                  {[
                    ["Loan", record.loanId],
                    ["Loan status", preview.loanStatus],
                    [
                      "Outstanding quantity",
                      number(preview.outstandingQuantity),
                    ],
                    [
                      "Received buy-in quantity",
                      number(preview.receivedQuantity),
                    ],
                    ["Purchase cost", rupees(preview.purchaseCost)],
                    [
                      "Cash collateral held",
                      rupees(preview.cashCollateralHeld),
                    ],
                    [
                      "Cash collateral to return",
                      rupees(preview.cashCollateralToReturn),
                    ],
                  ].map(([label, value]) => (
                    <div key={label}>
                      <dt>{label}</dt>
                      <dd>{value}</dd>
                    </div>
                  ))}
                </dl>
                {preview.closeout ? (
                  <p className="db-form-note">
                    Closed on {caseDate(preview.closeout.createdAt)} ·{" "}
                    {preview.closeout.reference}. Final fees and rebates follow
                    the agreement’s settlement rules.
                  </p>
                ) : (
                  <>
                    {!!preview.blockers?.length && (
                      <div role="status" className="db-form-error">
                        {preview.blockers.map((b) => (
                          <p key={b}>{b}</p>
                        ))}
                      </div>
                    )}
                    <p className="db-form-note">
                      Inventory will not be credited again. Remaining collateral
                      is released after buy-in costs are deducted. Final fee
                      billing and rebate processing are queued; payment status
                      updates after confirmation. BorrowDesk will also cancel
                      pending borrowed-security orders for this security.
                    </p>
                    <label className="db-form-note">
                      Closure reference
                      <input
                        required
                        maxLength={120}
                        disabled={busy}
                        value={reference}
                        onChange={(e) => setReference(e.target.value)}
                      />
                    </label>
                  </>
                )}
              </>
            )}
            {error && (
              <p className="db-form-error" role="alert">
                {error}
              </p>
            )}
            <footer>
              <button
                type="button"
                className="db-button"
                disabled={busy}
                onClick={() => setOpen(false)}
              >
                Cancel
              </button>
              {!preview?.closeout && (
                <>
                  <button
                    type="button"
                    className="db-button"
                    disabled={busy}
                    onClick={load}
                  >
                    Refresh checks
                  </button>
                  <button
                    className="db-button db-primary"
                    disabled={busy || !preview?.eligible}
                  >
                    {busy ? "Closing…" : "Confirm loan closure"}
                  </button>
                </>
              )}
            </footer>
          </form>
        </DefaultsBuyinsModal>
      )}
    </>
  );
}
