import { useState } from "react";
import Icon from "../common/Icon";
import { lenderRows, borrowerRows } from "../../data/marketingData";
export function PreviewTable({
  borrower = false,
  compact = false,
  search = "",
}) {
  const rows = (borrower ? borrowerRows : lenderRows).filter((row) =>
    Object.values(row).join(" ").toLowerCase().includes(search.toLowerCase()),
  );
  return (
    <div className="preview-table-scroll">
      <table className="preview-table">
        <thead>
          <tr>
            <th>{borrower ? "Locate ID" : "Loan ID"}</th>
            {!borrower && !compact && <th>Borrower</th>}
            <th>Security</th>
            <th>{borrower ? "Requested" : "Quantity"}</th>
            {!borrower && <th>Collateral</th>}
            {(borrower || !compact) && <th>Status</th>}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id}>
              <td>{row.id}</td>
              {!borrower && !compact && <td>{row.counterparty}</td>}
              <td>
                <strong>{row.security}</strong>
              </td>
              <td>{row.quantity}</td>
              {!borrower && <td>{row.collateral}</td>}
              {(borrower || !compact) && (
                <td>
                  <span
                    className={`status-pill${row.status === "Pending" ? " pending" : ""}`}
                  >
                    {row.status}
                  </span>
                </td>
              )}
            </tr>
          ))}
          {!rows.length && (
            <tr>
              <td colSpan={6} className="preview-empty">
                No sample records match your search.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
export default function WorkspacePreview() {
  const [desk, setDesk] = useState("lender"),
    [search, setSearch] = useState("");
  return (
    <div
      className="workspace-preview"
      aria-label="Illustrative Lendora workspace"
    >
      <aside className="preview-sidebar">
        <div className="preview-brand">
          <span>ϟ</span> Lendora
        </div>
        {[
          ["layers", "Loans"],
          ["document", "Agreements"],
          ["search", "Locates"],
          ["layers", "Collateral"],
          ["users", "Counterparties"],
        ].map(([icon, text], index) => (
          <div key={text} className={index === 0 ? "selected" : ""}>
            <Icon name={icon} size={15} />
            {text}
          </div>
        ))}
        <small>Illustrative workspace</small>
      </aside>
      <div className="preview-main">
        <div
          className="preview-tabs"
          role="tablist"
          aria-label="Workspace preview"
        >
          {[
            ["lender", "LendDesk"],
            ["borrower", "BorrowDesk"],
          ].map(([id, text]) => (
            <button
              key={id}
              type="button"
              role="tab"
              id={`tab-${id}`}
              aria-selected={desk === id}
              aria-controls="workspace-panel"
              className={desk === id ? "active" : ""}
              onClick={() => {
                setDesk(id);
                setSearch("");
              }}
            >
              {text}
            </button>
          ))}
        </div>
        <div
          id="workspace-panel"
          role="tabpanel"
          aria-labelledby={`tab-${desk}`}
        >
          <div className="preview-title">
            <h3>{desk === "lender" ? "Loans" : "Locate requests"}</h3>
            <label>
              <Icon name="search" size={13} />
              <input
                aria-label="Search sample workspace"
                placeholder="Search securities or counterparties"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </label>
          </div>
          <PreviewTable borrower={desk === "borrower"} search={search} />
        </div>
        <div className="preview-lifecycle">
          {[
            ["document", "Agreement"],
            ["search", "Locate"],
            ["layers", "Loan"],
            ["return", "Return"],
          ].map(([icon, text], index) => (
            <div key={text}>
              {index > 0 && <Icon name="arrow" size={15} />}
              <span>
                <Icon name={icon} size={16} />
                {text}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
