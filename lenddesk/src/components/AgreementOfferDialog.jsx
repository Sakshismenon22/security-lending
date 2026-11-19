import { useEffect, useRef, useState } from "react";
import { lenderGet, lenderPost } from "../api/lendDeskApi";
import MslaCollateralRules from "./MslaCollateralRules";
import "./MslaCollateralRules.css";
const label = (value) =>
  value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
const today = () =>
  new Date(Date.now() + 330 * 60000).toISOString().slice(0, 10);
const oneYearFromToday = () => {
  const value = new Date(Date.now() + 330 * 60000);
  value.setUTCFullYear(value.getUTCFullYear() + 1);
  return value.toISOString().slice(0, 10);
};
const decimalInput = (value) => Number(String(value).replace(/[₹,\s]/g, ""));

export default function AgreementOfferDialog({
  organizationId,
  borrowers,
  onClose,
  onSent,
}) {
  const [form, setForm] = useState(() => ({
    connectionId: borrowers[0]?.connectionId ?? "",
    number: `MSLA-${Date.now()}`,
    effectiveDate: today(),
    expiryDate: oneYearFromToday(),
    creditLimit: "10000000",
    feeRate: "0.03",
    rebateRate: "0.01",
    coverageRatio: "1.05",
    billingFrequency: "MONTHLY",
    rebateSettlementFrequency: "AT_CLOSE",
    dayBasis: "365",
    recallAllowed: true,
    marginHours: "24",
    recallHours: "48",
  }));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [rules, setRules] = useState(() => [
    {
      key: crypto.randomUUID(),
      assetType: "CASH",
      securityId: "",
      haircutPercent: "0",
    },
  ]);
  const [securities, setSecurities] = useState([]),
    [securityError, setSecurityError] = useState(""),
    [securityLoading, setSecurityLoading] = useState(true),
    [securityRevision, setSecurityRevision] = useState(0);
  const sending = useRef(false);
  useEffect(() => {
    const controller = new AbortController();
    lenderGet("/api/v1/securities", controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setSecurities(Array.isArray(data) ? data : data.content || []);
          setSecurityError("");
        }
      })
      .catch((error) => {
        if (!controller.signal.aborted) setSecurityError(error.message);
      })
      .finally(() => {
        if (!controller.signal.aborted) setSecurityLoading(false);
      });
    return () => controller.abort();
  }, [securityRevision]);

  const update = (key, value) =>
    setForm((current) => ({ ...current, [key]: value }));
  async function submit(event) {
    event.preventDefault();
    setError("");
    const creditLimit = decimalInput(form.creditLimit);
    if (!Number.isFinite(creditLimit) || creditLimit < 0.000001)
      return setError(
        "Credit limit must be a positive amount. You may enter ₹ and comma separators, for example 10,000,000.",
      );
    if (!form.connectionId)
      return setError(
        "Select the borrower connection that will receive this offer.",
      );
    if (form.expiryDate < form.effectiveDate)
      return setError("Expiry date cannot be before the effective date.");

    if (sending.current) return;
    if (!rules.length)
      return setError("Add at least one accepted collateral rule.");
    const seen = new Set(),
      payloadRules = [];
    for (const rule of rules) {
      const securityId =
        rule.assetType === "SECURITY" ? Number(rule.securityId) : null;
      const percent = Number(rule.haircutPercent);
      if (
        rule.assetType === "SECURITY" &&
        (!rule.securityId ||
          !securities.some((s) => Number(s.id) === securityId))
      )
        return setError(
          "Choose a loaded security for every security collateral rule.",
        );
      if (
        rule.haircutPercent === "" ||
        !Number.isFinite(percent) ||
        percent < 0 ||
        percent > 99.999999 ||
        (rule.assetType === "CASH" && percent !== 0)
      )
        return setError(
          "Haircuts must be between 0% and less than 100%. Cash requires 0%.",
        );
      const identity = `${rule.assetType}:${securityId}`;
      if (seen.has(identity))
        return setError("Each cash or security rule may appear only once.");
      seen.add(identity);
      payloadRules.push({
        assetType: rule.assetType,
        securityId,
        haircut: Number((percent / 100).toFixed(8)),
      });
    }
    sending.current = true;

    setSubmitting(true);
    try {
      const agreement = await lenderPost(
        `/api/v1/organizations/${organizationId}/agreements`,
        {
          connectionId: Number(form.connectionId),
          number: form.number,
          effectiveDate: form.effectiveDate,
          expiryDate: form.expiryDate,
          creditLimit,
          feeRate: Number(form.feeRate),
          rebateRate: Number(form.rebateRate),
          coverageRatio: Number(form.coverageRatio),
          billingFrequency: form.billingFrequency,
          rebateSettlementFrequency: form.rebateSettlementFrequency,
          dayBasis: Number(form.dayBasis),
          recallAllowed: form.recallAllowed,
          marginHours: Number(form.marginHours),
          recallHours: Number(form.recallHours),
          collateralRules: payloadRules,
        },
      );
      onSent(agreement);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      sending.current = false;
      setSubmitting(false);
    }
  }
  return (
    <div
      className="agreement-offer-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !submitting) onClose();
      }}
    >
      <section
        className="agreement-offer-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="send-msla-title"
      >
        <header>
          <div>
            <p>COUNTERPARTY TERMS</p>
            <h2 id="send-msla-title">Send MSLA offer</h2>
          </div>
          <button
            type="button"
            className="agreement-offer-close"
            onClick={onClose}
            disabled={submitting}
            aria-label="Close"
          >
            ×
          </button>
        </header>
        <p className="agreement-offer-intro">
          The borrower receives these terms as an offer and can only accept or
          reject them. No loan activity is permitted until both systems show{" "}
          <b>Active</b>.
        </p>
        <form onSubmit={submit}>
          <div className="agreement-offer-grid">
            <label>
              Borrower connection
              <select
                value={form.connectionId}
                onChange={(event) => update("connectionId", event.target.value)}
                required
              >
                <option value="">Select borrower</option>
                {borrowers.map((b) => (
                  <option key={b.connectionId} value={b.connectionId}>
                    {b.name} ({b.externalId})
                  </option>
                ))}
              </select>
            </label>
            <label>
              Agreement reference
              <input
                value={form.number}
                maxLength="100"
                onChange={(event) => update("number", event.target.value)}
                required
              />
            </label>
            <label>
              Effective date
              <input
                type="date"
                value={form.effectiveDate}
                onChange={(event) =>
                  update("effectiveDate", event.target.value)
                }
                required
              />
            </label>
            <label>
              Expiry date
              <input
                type="date"
                value={form.expiryDate}
                onChange={(event) => update("expiryDate", event.target.value)}
                required
              />
            </label>
            <label>
              Credit limit (₹)
              <input
                type="text"
                inputMode="decimal"
                placeholder="10,000,000"
                value={form.creditLimit}
                onChange={(event) => update("creditLimit", event.target.value)}
                required
              />
            </label>
            <label>
              Fee rate (annual decimal)
              <input
                type="number"
                min="0"
                max="1"
                step="0.0001"
                value={form.feeRate}
                onChange={(event) => update("feeRate", event.target.value)}
                required
              />
            </label>
            <label>
              Cash rebate rate (annual decimal)
              <input
                type="number"
                min="0"
                max="1"
                step="0.0001"
                value={form.rebateRate}
                onChange={(event) => update("rebateRate", event.target.value)}
                required
              />
            </label>
            <label>
              Collateral ratio
              <input
                type="number"
                min="1"
                max="5"
                step="0.01"
                value={form.coverageRatio}
                onChange={(event) =>
                  update("coverageRatio", event.target.value)
                }
                required
              />
            </label>
            <label>
              Fee billing
              <select
                value={form.billingFrequency}
                onChange={(event) =>
                  update("billingFrequency", event.target.value)
                }
              >
                <option value="MONTHLY">Monthly</option>
                <option value="AT_CLOSE">At loan termination</option>
              </select>
            </label>
            <label>
              Rebate settlement
              <select
                value={form.rebateSettlementFrequency}
                onChange={(event) =>
                  update("rebateSettlementFrequency", event.target.value)
                }
              >
                {["DAILY", "WEEKLY", "MONTHLY", "AT_CLOSE"].map((value) => (
                  <option key={value} value={value}>
                    {label(value)}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Day basis
              <select
                value={form.dayBasis}
                onChange={(event) => update("dayBasis", event.target.value)}
              >
                <option value="360">360</option>
                <option value="365">365</option>
              </select>
            </label>
            <label>
              Margin notice (hours)
              <input
                type="number"
                min="1"
                step="1"
                value={form.marginHours}
                onChange={(event) => update("marginHours", event.target.value)}
                required
              />
            </label>
            <label>
              Recall notice (hours)
              <input
                type="number"
                min="1"
                step="1"
                value={form.recallHours}
                onChange={(event) => update("recallHours", event.target.value)}
                required
              />
            </label>
            <label className="agreement-offer-check">
              <input
                type="checkbox"
                checked={form.recallAllowed}
                onChange={(event) =>
                  update("recallAllowed", event.target.checked)
                }
              />{" "}
              Recall permitted under this MSLA
            </label>
          </div>
          <MslaCollateralRules
            rules={rules}
            onChange={setRules}
            securities={securities}
            loading={securityLoading}
            error={securityError}
            onRefresh={() => {
              setSecurityLoading(true);
              setSecurityRevision((value) => value + 1);
            }}
            disabled={submitting}
          />
          {error && (
            <p className="live-error" role="alert">
              {error}
            </p>
          )}
          <footer>
            <button type="button" onClick={onClose} disabled={submitting}>
              Cancel
            </button>
            <button className="ref-primary" type="submit" disabled={submitting}>
              {submitting ? "Sending…" : "Send to borrower"}
            </button>
          </footer>
        </form>
      </section>
    </div>
  );
}
