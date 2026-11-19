import { useRef, useState } from "react";
import { defaultsBuyinsApi } from "../api/defaultsBuyinsApi";
import { rupees, number } from "../data/defaultsBuyinsData";
import DefaultsBuyinsModal from "./DefaultsBuyinsModal";

export default function CollateralSalePanel({
  record,
  organizationId,
  onRefresh,
}) {
  const [preview, setPreview] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const sending = useRef(false),
    clientId = useRef(null);
  const funding = record.collateralFunding;
  if (!funding) return null;
  const maySell =
    funding.needsSale &&
    record.loanStatus !== "CLOSED" &&
    ["ACCEPTED", "COMPLETED"].includes(record.request?.status);
  async function review() {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      const data = await defaultsBuyinsApi.collateralSalePreview(
        organizationId,
        record.backendId,
      );
      clientId.current = `collateral-sale-${crypto.randomUUID()}`;
      setPreview(data);
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  async function sell() {
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await defaultsBuyinsApi.sellCollateral(organizationId, record.backendId, {
        reference: clientId.current,
        saleScope: preview.saleScope,
      });
      setPreview(null);
      onRefresh();
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  return (
    <section
      className="db-collateral-sale"
      aria-label="Buy-in collateral funding"
    >
      {funding.needsSale ? (
        <>
          <b>1. Sell securities collateral</b>
          <p>
            Sell the pledged securities into cash before placing the buy-in
            order. Proceeds remain held against this loan.
          </p>
          {maySell && (
            <button
              className="db-button db-primary"
              disabled={busy}
              onClick={review}
            >
              {busy ? "Loading…" : "Sell securities collateral"}
            </button>
          )}
        </>
      ) : (
        funding.sales?.length > 0 && (
          <>
            <b>Collateral sale complete</b>
            <p>
              Cash collateral available: {rupees(funding.cashHeld)}. Place the
              buy-in order next; settlement returns any surplus to the borrower.
            </p>
          </>
        )
      )}
      {error && !preview && (
        <p role="alert" className="db-form-error">
          {error}
        </p>
      )}
      {preview && (
        <DefaultsBuyinsModal
          title="Sell securities collateral"
          description="Internal demo market sale of all the pledged security lots listed below. Quotes may change before confirmation. Proceeds stay as cash collateral; no external broker order is sent."
          onClose={busy ? null : () => setPreview(null)}
        >
          <div className="db-collateral-sale-lots">
            {preview.securityLots.map((lot) => (
              <article key={lot.positionId}>
                <b>{lot.symbol}</b>
                <span>
                  {number(lot.quantity)} securities ×{" "}
                  {lot.price == null ? "Quote unavailable" : rupees(lot.price)}
                </span>
                <strong>
                  {lot.proceeds == null
                    ? "Awaiting quote"
                    : rupees(lot.proceeds)}
                </strong>
              </article>
            ))}
          </div>
          <dl className="db-buyin-purchase-estimate">
            <div>
              <dt>Existing cash collateral</dt>
              <dd>{rupees(preview.cashHeld)}</dd>
            </div>
            <div>
              <dt>Estimated sale proceeds</dt>
              <dd>
                {preview.estimatedProceeds == null
                  ? "Awaiting quote"
                  : rupees(preview.estimatedProceeds)}
              </dd>
            </div>
          </dl>
          <p className="db-form-note">
            Confirm the sale first. You will then place the buy-in order
            separately. Sale proceeds must cover the purchase and its execution
            fees before the order can fill.
          </p>
          {preview.blockers.map((message, i) => (
            <p key={i} className="db-form-error">
              {message}
            </p>
          ))}
          {error && (
            <p role="alert" className="db-form-error">
              {error}
            </p>
          )}
          <footer>
            <button
              className="db-button"
              disabled={busy}
              onClick={() => setPreview(null)}
            >
              Cancel
            </button>
            <button
              className="db-button db-primary"
              disabled={busy || !preview.canSell}
              onClick={sell}
            >
              {busy ? "Selling…" : "Confirm sale"}
            </button>
          </footer>
        </DefaultsBuyinsModal>
      )}
    </section>
  );
}
