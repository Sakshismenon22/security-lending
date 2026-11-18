import { number, rupees, caseDate } from "../data/defaultsBuyinsData";

const Status = ({ children }) => (
  <span
    className={`db-badge ${/FAIL|REJECT/i.test(children) ? "db-risk" : /COMPLETED|SETTLED|EXECUTED/i.test(children) ? "db-good" : "db-purple"}`}
  >
    {children}
  </span>
);
export function LiveBuyinExecution({ record, onRequest, allowed }) {
  return (
    <article className="db-card">
      <h3>Buy-in reports &amp; requests</h3>
      {record.request && (
        <div className="db-live-request">
          <b>Request #{record.request.id}</b>
          <Status>{record.request.status}</Status>
          {record.request.failureReason && (
            <p role="status">{record.request.failureReason}</p>
          )}
        </div>
      )}
      {record.reports.length ? (
        <div className="db-scroll">
          <table>
            <thead>
              <tr>
                <th>Buy-in</th>
                <th>Quantity</th>
                <th>Reported price</th>
                <th>Costs</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {record.reports.map((report) => (
                <tr key={report.id}>
                  <td>BI-{String(report.id).padStart(6, "0")}</td>
                  <td>{number(report.quantity)}</td>
                  <td>{rupees(report.price)}</td>
                  <td>{rupees(report.costs)}</td>
                  <td>
                    <Status>{report.status}</Status>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="db-empty">No execution report has been received.</p>
      )}
      <p className="db-footnote">
        Execution reports do not confirm custody movement, collateral
        liquidation or cash settlement.
      </p>
      {allowed && (
        <button className="db-button" onClick={onRequest}>
          {record.request ? "Request another attempt" : "Request buy-in"}
        </button>
      )}
    </article>
  );
}
export function LiveBuyinRecovery({ record }) {
  const report = record.reports[0];
  return (
    <article className="db-card">
      <h3>Owner-reported amounts</h3>
      <table>
        <thead>
          <tr>
            <th>Description</th>
            <th className="db-num">Amount</th>
          </tr>
        </thead>
        <tbody>
          {[
            ["Reported buy-in costs", report?.costs],
            ["Reported collateral applied", report?.collateralApplied],
            ["Reported residual balance", report?.residualBalance],
          ].map(([label, value]) => (
            <tr key={label}>
              <td>{label}</td>
              <td className="db-num">{rupees(value)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="db-card-bottom">
        <b>Settlement confirmation</b>
        <Status>Not supplied by this report</Status>
      </div>
      <p className="db-footnote">
        No ledger movements or loan close-out are inferred from a buy-in report.
      </p>
    </article>
  );
}
export function LiveObligation({ record }) {
  return (
    <article className="db-card">
      <h3>Default &amp; obligation</h3>
      <dl>
        {[
          ["Trigger", record.trigger],
          ["Reason", record.reason],
          ["Borrower", record.borrower],
          ["Loan", record.loanId],
          ["Source reference", record.reference],
          ["Reported quantity", number(record.quantity)],
          ["Deadline (IST)", caseDate(record.deadline)],
          ["Owner reference", record.ownerReferenceId],
        ].map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value || "—"}</dd>
          </div>
        ))}
      </dl>
    </article>
  );
}
