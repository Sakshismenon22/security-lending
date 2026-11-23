import "./BuyinClosureSummary.css";
const value = (row, ...names) => {
  for (const name of names) if (row?.[name] != null) return row[name];
  return null;
};
const money = (amount) =>
  amount == null
    ? "Not supplied"
    : "₹" +
      Number(amount).toLocaleString("en-IN", { maximumFractionDigits: 2 });
const number = (amount) =>
  amount == null ? "Not supplied" : Number(amount).toLocaleString("en-IN");
const status = (row) => String(row.status || "").toUpperCase();
const amount = (row, kind) =>
  Number(
    kind === "fee"
      ? value(row, "feeAmount", "fee_amount", "amount")
      : row.amount,
  ) || 0;
function Billing({ kind, records, rate }) {
  const paid = records
    .filter((r) => status(r) === "PAID")
    .reduce((sum, r) => sum + amount(r, kind), 0);
  const outstanding = records
    .filter((r) => ["DUE", "ACCRUED"].includes(status(r)))
    .reduce((sum, r) => sum + amount(r, kind), 0);
  const text = !records.length
    ? rate != null && Number(rate) === 0
      ? `No ${kind} rate`
      : `Awaiting ${kind === "fee" ? "final billing" : "rebate confirmation"}`
    : outstanding > 0
      ? kind === "fee"
        ? "Payment due"
        : "Awaiting payment"
      : records.every((r) => status(r) === "PAID")
        ? "Received records paid"
        : "Confirmation pending";
  return (
    <div className="lw-closeout-billing">
      <span>{kind === "fee" ? "Fee settlement" : "Rebate settlement"}</span>
      <strong>{text}</strong>
      <small>
        {records.length
          ? `${records.length} received record${records.length === 1 ? "" : "s"} · ${money(paid)} paid · ${money(outstanding)} ${kind === "fee" ? "due" : "accrued"}`
          : "Updates after the lender confirms final billing and payment."}
      </small>
    </div>
  );
}
export default function BuyinClosureSummary({
  summary,
  pending,
  fees = [],
  rebates = [],
  feeRate,
  rebateRate,
}) {
  if (!summary && !pending) return null;
  const closedAt = value(summary, "closedAt", "closed_at");
  const fields = [
    ["Closed quantity", number(value(summary, "quantity")) + " shares"],
    [
      "Average buy-in price",
      money(value(summary, "averagePrice", "average_price")),
    ],
    [
      "Securities purchase value",
      money(value(summary, "purchaseValue", "purchase_value")),
    ],
    [
      "Execution costs",
      money(value(summary, "executionCosts", "execution_costs")),
    ],
    [
      "Total buy-in cost",
      money(value(summary, "purchaseCost", "purchase_cost")),
    ],
    [
      "Cash collateral applied",
      money(value(summary, "cashCollateralApplied", "cash_collateral_applied")),
    ],
    ...(Number(value(summary, "lenderFundsApplied")) > 0
      ? [["Lender funds applied", money(value(summary, "lenderFundsApplied"))]]
      : []),
    [
      "Cash collateral returned",
      money(
        value(summary, "cashCollateralReturned", "cash_collateral_returned"),
      ),
    ],
    [
      "Closed at (IST)",
      closedAt
        ? new Date(closedAt).toLocaleString("en-IN", {
            dateStyle: "medium",
            timeStyle: "short",
            timeZone: "Asia/Kolkata",
          })
        : "Not supplied",
    ],
    ["Closure reference", value(summary, "reference") || "Not supplied"],
  ];
  return (
    <section
      className="lw-panel lw-closeout"
      aria-labelledby="lw-buyin-closure-title"
    >
      <header>
        <div>
          <h2 id="lw-buyin-closure-title">Buy-in loan closure</h2>
          <p>
            The lender closed this loan using purchased replacement securities.
          </p>
        </div>
        <span className="lw-closeout-badge">
          {summary ? "Closed through buy-in" : "Summary pending"}
        </span>
      </header>
      {summary ? (
        <>
          <dl className="lw-closeout-fields">
            {fields.map(([label, field]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{field}</dd>
              </div>
            ))}
          </dl>
          <div className="lw-closeout-settlements">
            <Billing kind="fee" records={fees} rate={feeRate} />
            <Billing kind="rebate" records={rebates} rate={rebateRate} />
          </div>
          <p className="lw-closeout-note">
            Buy-in costs were deducted from cash collateral. The cash amount
            above is the surplus returned, not a rebate. Fee and rebate payment
            statuses follow their separate confirmations. Security collateral
            releases are listed in the Collateral tab.
          </p>
        </>
      ) : (
        <p className="lw-closeout-pending" role="status">
          Loan closure is confirmed. Awaiting the lender’s settlement summary;
          purchase costs and collateral amounts will appear when received.
        </p>
      )}
    </section>
  );
}
