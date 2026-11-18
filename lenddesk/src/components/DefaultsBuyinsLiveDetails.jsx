import BuyinOrderPanel from "./BuyinOrderPanel";
import { canReportBuyin } from "../api/defaultsBuyinsApi";
import { number, rupees, caseDate } from "../data/defaultsBuyinsData";

const Status = ({ children }) => (
  <span
    className={`db-badge ${/FAIL|REJECT/i.test(children) ? "db-risk" : /COMPLETED|SETTLED|EXECUTED/i.test(children) ? "db-good" : "db-purple"}`}
  >
    {children}
  </span>
);
export function LiveBuyinExecution({
  record,
  onRequest,
  onReport,
  allowed,
  organizationId,
  onRefresh,
}) {
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
      {record.executionRequest && (
        <div className="db-live-request">
          <b>Execution submission #{record.executionRequest.id}</b>
          <Status>{record.executionRequest.status}</Status>
          {record.executionRequest.failureReason && (
            <p role="status">{record.executionRequest.failureReason}</p>
          )}
        </div>
      )}
      {canReportBuyin(record) && (
        <button className="db-button db-primary" onClick={onReport}>
          Record execution
        </button>
      )}
      <BuyinOrderPanel
        record={record}
        organizationId={organizationId}
        onRefresh={onRefresh}
      />
      {record.reports.length ? (
        <div className="db-scroll">
          <table>
            <thead>
              <tr>
                <th>Buy-in</th>
                <th>Quantity</th>
                <th>Reported price</th>
                <th>Costs</th>
                <th>Trade reference</th>
                <th>Inventory receipt</th>
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
                  <td>{report.executionReference || "—"}</td>
                  <td>
                    {report.inventoryReceivedAt
                      ? `Received · ${caseDate(report.inventoryReceivedAt)}`
                      : "Awaiting receipt"}
                  </td>
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
      {record.obligationType === "MARGIN_CALL" && (
        <p className="db-footnote">
          This is a collateral obligation. It is resolved by fulfilling the
          margin call; a securities buy-in is not available.
        </p>
      )}
      <p className="db-footnote">
        Inventory is credited once securities receipt is confirmed. Internal
        demo orders include simulated receipt; external trade reports require
        receipt confirmation. Cash settlement and the original loan remain
        separate.
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
            [
              "Reported execution amount before settlement",
              report?.residualBalance,
            ],
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
        <Status>
          {report?.inventoryReceivedAt
            ? "Inventory received · cash payable"
            : "Awaiting securities receipt"}
        </Status>
      </div>
      <p className="db-footnote">
        Received purchases create a payable ledger entry. They do not
        automatically close the original loan or settle cash.
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
