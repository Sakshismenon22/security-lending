import { useEffect, useRef, useState } from "react";
import { Plus, Trash2, X } from "lucide-react";
import { lendingApi } from "../../api/lendingApi";
import "./LoanCollateralModal.css";
const money = (value) =>
  value == null
    ? "—"
    : new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: "INR",
        maximumFractionDigits: 2,
      }).format(value);
const percent = (value) =>
  value == null
    ? "—"
    : `${Number(value).toLocaleString("en-IN", { maximumFractionDigits: 4 })}%`;
const field = (row, camel, snake) => row?.[camel] ?? row?.[snake];

export default function LoanCollateralModal({
  reservation,
  onClose,
  onSubmitted,
}) {
  const reservationId = Number(reservation._id ?? reservation.id);
  const remaining = Number(
    field(reservation, "remainingQuantity", "remaining_quantity"),
  );
  const [quantity, setQuantity] = useState(String(remaining));
  const [maturity, setMaturity] = useState("");
  const [allocations, setAllocations] = useState([]);
  const [valuation, setValuation] = useState({
    key: "",
    data: null,
    error: "",
  });
  const [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  const sending = useRef(false),
    sequence = useRef(0);
  const collateral = allocations.map((row) => ({
    assetType: row.asset === "CASH" ? "CASH" : "SECURITY",
    securityId: row.asset === "CASH" ? null : Number(row.asset.split(":")[1]),
    quantity: row.asset === "CASH" ? 0 : Number(row.units || 0),
    cashAmount: row.asset === "CASH" ? Number(row.units || 0) : 0,
  }));
  const requestKey = JSON.stringify({
    reservationId,
    quantity: Number(quantity),
    collateral,
  });
  useEffect(() => {
    const controller = new AbortController();
    let active = true,
      inFlight = false;
    async function load() {
      if (inFlight) return;
      inFlight = true;
      try {
        const body = JSON.parse(requestKey);
        if (!Number.isSafeInteger(body.quantity) || body.quantity <= 0)
          throw new Error("Enter a positive whole loan quantity");
        const data = await lendingApi.loanCollateralPreview(
          body,
          controller.signal,
        );
        if (active) setValuation({ key: requestKey, data, error: "" });
      } catch (e) {
        if (active && !controller.signal.aborted)
          setValuation((previous) => ({
            ...previous,
            key: requestKey,
            error: e.message,
          }));
      } finally {
        inFlight = false;
      }
    }
    const debounce = setTimeout(load, 200);
    const timer = setInterval(() => {
      if (!document.hidden) load();
    }, 10000);
    const focus = () => load();
    window.addEventListener("focus", focus);
    return () => {
      active = false;
      controller.abort();
      clearTimeout(debounce);
      clearInterval(timer);
      window.removeEventListener("focus", focus);
    };
  }, [requestKey]);
  const options = valuation.data?.options ?? [];
  const current =
    valuation.key === requestKey && !valuation.error ? valuation.data : null;
  const updating = valuation.key !== requestKey;
  const update = (id, patch) =>
    setAllocations((rows) =>
      rows.map((row) => (row.id === id ? { ...row, ...patch } : row)),
    );
  function add() {
    const option = options[0];
    if (!option) return;
    setAllocations((rows) => [
      ...rows,
      { id: ++sequence.current, asset: option.key, units: "" },
    ]);
    setError("");
  }
  async function submit(event) {
    event.preventDefault();
    if (sending.current || !current?.eligible || updating) return;
    const maturityAt = new Date(`${maturity}+05:30`);
    if (
      !maturity ||
      !Number.isFinite(+maturityAt) ||
      +maturityAt <= Date.now()
    ) {
      setError("Loan maturity must be in the future");
      return;
    }
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      const result = await lendingApi.createLoan({
        ...JSON.parse(requestKey),
        maturityAt: maturityAt.toISOString(),
      });
      onSubmitted(result);
    } catch (e) {
      setError(
        `${e.message}. Refresh reservations to check the request before retrying if the result is uncertain.`,
      );
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  const close = () => {
    if (!sending.current) onClose();
  };
  return (
    <div className="res-modal-backdrop" onClick={close}>
      <form
        className="loan-collateral-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="loan-collateral-title"
        onSubmit={submit}
        onClick={(event) => event.stopPropagation()}
      >
        <header>
          <div>
            <h2 id="loan-collateral-title">Convert reservation to loan</h2>
            <p>
              {reservation.securityLabel} · {reservation.lenderLabel} · Multiple
              collateral allocations
            </p>
          </div>
          <button
            type="button"
            aria-label="Close loan request"
            disabled={busy}
            onClick={close}
          >
            <X size={18} />
          </button>
        </header>
        <div className="loan-collateral-body">
          <div className="loan-collateral-terms">
            <label>
              Loan quantity
              <input
                required
                type="number"
                min="1"
                max={remaining}
                step="1"
                disabled={busy}
                value={quantity}
                onChange={(event) => setQuantity(event.target.value)}
              />
            </label>
            <label>
              Loan maturity (IST)
              <input
                required
                type="datetime-local"
                disabled={busy}
                value={maturity}
                onChange={(event) => setMaturity(event.target.value)}
              />
            </label>
            <div>
              <small>Loan market value</small>
              <strong>{money(current?.loanMarketValue)}</strong>
            </div>
            <div>
              <small>Required eligible collateral</small>
              <strong>{money(current?.requiredCollateral)}</strong>
              <small>
                {current ? percent(Number(current.coverageRatio) * 100) : "—"}{" "}
                MSLA coverage
              </small>
            </div>
          </div>
          <div className="loan-collateral-heading">
            <h3>Proposed collateral</h3>
            <button
              className="res-btn primary"
              type="button"
              disabled={busy || !options.length || allocations.length >= 50}
              onClick={add}
            >
              <Plus size={14} />
              Add collateral
            </button>
          </div>
          <p className="loan-collateral-help">
            Mix cash and eligible securities. Market value is the cash amount or
            quantity × quoted price. Eligible value applies the MSLA haircut.
            Available securities exclude pledged and blocked holdings. Repeated
            assets share the same available balance.
          </p>
          <div className="loan-collateral-table-scroll">
            <table aria-label="Proposed collateral allocations">
              <thead>
                <tr>
                  <th>Collateral asset</th>
                  <th>Cash / quantity</th>
                  <th>Available</th>
                  <th>Unit price</th>
                  <th>Market value</th>
                  <th>Haircut</th>
                  <th>Eligible value</th>
                  <th aria-label="Remove collateral" />
                </tr>
              </thead>
              <tbody>
                {allocations.map((row, index) => {
                  const option = options.find(
                      (option) => option.key === row.asset,
                    ),
                    calculation = current?.rows[index];
                  return (
                    <tr key={row.id}>
                      <td>
                        <select
                          aria-label={`Collateral asset ${index + 1}`}
                          disabled={busy}
                          value={row.asset}
                          onChange={(event) =>
                            update(row.id, {
                              asset: event.target.value,
                              units: "",
                            })
                          }
                        >
                          {options.map((option) => (
                            <option key={option.key} value={option.key}>
                              {option.symbol}
                              {option.assetType === "SECURITY"
                                ? " · Security"
                                : " · Cash"}
                            </option>
                          ))}
                        </select>
                        {calculation?.errors.map((message) => (
                          <small
                            className="loan-collateral-row-error"
                            key={message}
                          >
                            {message}
                          </small>
                        ))}
                      </td>
                      <td>
                        <input
                          required
                          aria-label={`${row.asset === "CASH" ? "Cash amount" : "Security quantity"} ${index + 1}`}
                          disabled={busy}
                          type="number"
                          min={row.asset === "CASH" ? "0.000001" : "1"}
                          step={row.asset === "CASH" ? "0.000001" : "1"}
                          value={row.units}
                          onChange={(event) =>
                            update(row.id, { units: event.target.value })
                          }
                        />
                      </td>
                      <td>
                        {option?.assetType === "CASH"
                          ? money(option.available)
                          : (option?.available?.toLocaleString("en-IN") ?? "—")}
                      </td>
                      <td>{money(calculation?.unitPrice)}</td>
                      <td>{money(calculation?.marketValue)}</td>
                      <td>
                        {calculation?.haircut == null
                          ? "—"
                          : percent(Number(calculation.haircut) * 100)}
                      </td>
                      <td>
                        <b>{money(calculation?.eligibleValue)}</b>
                      </td>
                      <td>
                        <button
                          type="button"
                          disabled={busy}
                          aria-label={`Remove collateral ${index + 1}`}
                          title="Remove collateral"
                          onClick={() =>
                            setAllocations((rows) =>
                              rows.filter((item) => item.id !== row.id),
                            )
                          }
                        >
                          <Trash2 size={15} />
                        </button>
                      </td>
                    </tr>
                  );
                })}
                {!allocations.length && (
                  <tr>
                    <td colSpan="8" className="loan-collateral-empty">
                      Add collateral to build the allocation table.
                    </td>
                  </tr>
                )}
              </tbody>
              <tfoot>
                <tr>
                  <th colSpan="4">Total proposed collateral</th>
                  <td>{money(current?.marketValue)}</td>
                  <td>After haircuts</td>
                  <td>
                    <b>{money(current?.eligibleValue)}</b>
                  </td>
                  <td />
                </tr>
              </tfoot>
            </table>
          </div>
          <div className="loan-collateral-totals" aria-live="polite">
            <div>
              <small>Coverage</small>
              <b>{percent(current?.coveragePercent)}</b>
            </div>
            <div>
              <small>Shortfall</small>
              <b>{money(current?.shortfall)}</b>
            </div>
            <div>
              <small>Excess eligible collateral</small>
              <b>{money(current?.surplus)}</b>
            </div>
            <div
              className={current?.eligible ? "sufficient" : "shortfall"}
              role="status"
            >
              {updating
                ? "Updating valuation…"
                : current?.eligible
                  ? "Coverage sufficient"
                  : "Coverage incomplete"}
            </div>
          </div>
          <p className="loan-collateral-help">
            Indicative values refresh as you edit and every ten seconds. Final
            prices, eligibility, credit limits and balances are checked again
            when the request is submitted.
          </p>
          {valuation.error && (
            <p role="alert" className="loan-collateral-error">
              {valuation.error}
            </p>
          )}
          {current?.errors.length > 0 && (
            <ul className="loan-collateral-error">
              {current.errors.map((message, index) => (
                <li key={`${index}:${message}`}>{message}</li>
              ))}
            </ul>
          )}
          {error && (
            <p role="alert" className="loan-collateral-error">
              {error}
            </p>
          )}
        </div>
        <footer>
          <button
            type="button"
            className="res-btn light"
            disabled={busy}
            onClick={close}
          >
            Cancel
          </button>
          <button
            className="res-btn primary"
            disabled={busy || updating || !current?.eligible || !maturity}
          >
            {busy ? "Submitting…" : "Submit loan request"}
          </button>
        </footer>
      </form>
    </div>
  );
}
