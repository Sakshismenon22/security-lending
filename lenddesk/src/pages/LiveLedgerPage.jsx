import { useEffect, useState } from "react";
import { Link, useOutletContext, useSearchParams } from "react-router-dom";
import LenderFunding from "../components/LenderFunding";
import ReferenceTable from "../components/reference/ReferenceTable";
import ReferenceRegister from "../components/reference/ReferenceRegister";
import {
  FinanceState,
  ReferenceMetrics,
} from "../components/reference/ReferenceControls";
import { lenderGet } from "../api/lendDeskApi";
import { useDataSource } from "../hooks/useDataSource";
import "../components/PaginatedTable.css";
import "./ReferencePages.css";
import "./FinanceReferencePages.css";

const money = (value) =>
  new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(Number(value ?? 0));
const label = (value) =>
  String(value ?? "—")
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (letter) => letter.toUpperCase());
const loanLabel = (id) =>
  id == null ? "—" : `LOAN-${String(id).padStart(6, "0")}`;
const ledgerId = (id) => `LED-${String(id).padStart(6, "0")}`;
const timestamp = (value) =>
  value
    ? new Intl.DateTimeFormat("en-GB", {
        timeZone: "Asia/Kolkata",
        dateStyle: "medium",
        timeStyle: "medium",
      })
        .format(new Date(value))
        .replace(",", "")
    : "—";
const columns = [
  ["timestamp", "IST Timestamp"],
  ["id", "Ledger ID"],
  ["account", "Account"],
  ["type", "Entry Type"],
  ["debit", "Debit"],
  ["credit", "Credit"],
  ["executionFeeAmount", "Execution fee (included)"],
  ["currency", "Currency"],
  ["borrower", "Borrower"],
  ["loan", "Loan ID"],
  ["reference", "Reference Type / ID"],
  ["payment", "Payment Request"],
  ["settlement", "Settlement State"],
  ["status", "Status"],
];
const blank = { account: "", type: "", connectionId: "", loanId: "" };
const sortable = new Set([
  "timestamp",
  "id",
  "type",
  "debit",
  "credit",
  "borrower",
  "loan",
  "reference",
]);

function accountFor(type) {
  if (/lender[ _]funding/i.test(type)) return "Lender funding account";
  if (/rebate/i.test(type)) return "Cash collateral / rebate payable";
  if (/fee/i.test(type)) return "Lender receivables / cash account";
  if (/buy.?in|default/i.test(type)) return "Securities buy-in clearing";
  return "Lender lifecycle settlement clearing";
}

function JournalDetails({ row }) {
  const [tab, setTab] = useState("Journal lines");
  const journal = {
    title: "▱ Journal Lines (2)",
    headers: ["Line#", "Account", "Description", "Debit", "Credit", "Currency"],
    rows: [
      [
        1,
        row.account,
        `Record ${row.type.toLowerCase()} from ${row.reference}`,
        money(row.amount),
        "—",
        row.currency,
      ],
      [
        2,
        "Lender settlement control account",
        `Balance ${row.type.toLowerCase()} entry`,
        "—",
        money(row.amount),
        row.currency,
      ],
      [
        "Total",
        "(balanced)",
        "",
        money(row.amount),
        money(row.amount),
        row.currency,
      ],
    ],
  };
  const lifecycle = {
    title: "♧ Linked Lifecycle Record",
    headers: [
      "Type",
      "Record ID",
      "Description",
      "Amount",
      "Currency",
      "State",
    ],
    rows: [
      [
        row.type,
        row.reference,
        "Source lifecycle event recorded by the lender service",
        money(row.amount),
        row.currency,
        <FinanceState value={row.settlement} />,
      ],
    ],
  };
  const table = tab === "Journal lines" ? journal : lifecycle;
  return (
    <div className="finance-details ledger-journal">
      <header>
        <h2>▤ Journal Entry {row.id}</h2>
        <FinanceState value="BALANCED" />
        <span>(2 lines, Δ ₹0.00)</span>
        <small>
          Borrower: {row.borrower} | Loan: {row.loan} | Account: {row.account} |
          Entry Type: {row.type} | Posted: {row.timestamp} IST
        </small>
      </header>
      {row.executionFeeAmount != null && <p>Execution fee: <b>{money(row.executionFeeAmount)}</b>, included in this {money(Math.abs(row.amount))} buy-in entry. It is not an additional charge.</p>}
      <div className="live-ledger-tabs" role="tablist">
        <button
          type="button"
          className={tab === "Journal lines" ? "active" : ""}
          aria-selected={tab === "Journal lines"}
          onClick={() => setTab("Journal lines")}
        >
          Journal lines (2)
        </button>
        <button
          type="button"
          className={tab === "Lifecycle record" ? "active" : ""}
          aria-selected={tab === "Lifecycle record"}
          onClick={() => setTab("Lifecycle record")}
        >
          Linked lifecycle record
        </button>
      </div>
      <div className="live-ledger-detail-table">
        <ReferenceTable
          title={table.title}
          headers={table.headers}
          rows={table.rows}
        />
      </div>
      <ReferenceTable
        title="◎ Audit Trail"
        headers={["IST Timestamp", "Event", "Actor", "Details"]}
        rows={[
          [
            row.timestamp,
            <FinanceState value="POSTED" />,
            "lifecycle-service",
            `Ledger entry ${row.id} posted from source reference ${row.reference}.`,
          ],
        ]}
      />
    </div>
  );
}

export default function LiveLedgerPage() {
  const { searchTerm = "" } = useOutletContext();
  const { organizationId, refreshKey } = useDataSource();
  const [fundingRevision, setFundingRevision] = useState(0);
  const [params, setParams] = useSearchParams();
  const reference = params.get("reference") ?? "";
  const [expanded, setExpanded] = useState({ scope: "", id: null });
  const [draft, setDraft] = useState(blank);
  const [filters, setFilters] = useState(blank);
  const [sort, setSort] = useState({ key: "id", direction: "desc" });
  const [paging, setPaging] = useState({ scope: "", page: 0, size: 25 });
  const filterScope = JSON.stringify([
    organizationId,
    filters,
    reference,
    searchTerm,
    sort,
  ]);
  const page = paging.scope === filterScope ? paging.page : 0;
  const query = new URLSearchParams(
    Object.entries({
      ...filters,
      q: searchTerm.trim(),
      reference,
      page,
      size: paging.size,
      sort: sort.key,
      direction: sort.direction,
    }).filter(([, value]) => value !== ""),
  );
  const queryText = query.toString(),
    scope = `${organizationId}:${refreshKey}:${fundingRevision}:${queryText}`;
  const [state, setState] = useState({ scope: "", data: null, error: "" });
  useEffect(() => {
    const controller = new AbortController();
    lenderGet(
      `/api/v1/organizations/${organizationId}/lenddesk/ledger/overview?${queryText}`,
      controller.signal,
    )
      .then((data) => {
        if (controller.signal.aborted) return;
        const last = Math.max(0, data.totalPages - 1);
        if (page > last) {
          setPaging((current) => ({
            ...current,
            scope: filterScope,
            page: last,
          }));
          return;
        }
        setState({ scope, data, error: "" });
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setState({ scope, data: null, error: error.message });
      });
    return () => controller.abort();
  }, [organizationId, scope, queryText, filterScope, page]);
  const loading = state.scope !== scope,
    error = loading ? "" : state.error;
  const data = loading ? null : state.data;
  const options = data?.options ??
    (state.scope.startsWith(`${organizationId}:`)
      ? state.data?.options
      : null) ?? { types: [], borrowers: [], loans: [] };
  const entries = (data?.content ?? []).map((entry) => {
    const amount = Number(entry.amount ?? 0),
      type = label(entry.type);
    return {
      id: ledgerId(entry.id),
      rawId: entry.id,
      timestamp: timestamp(entry.createdAt),
      account: accountFor(type),
      type,
      debit: amount,
      credit: amount,
      amount,
      executionFeeAmount: entry.executionFeeAmount == null ? null : Number(entry.executionFeeAmount),
      currency: "INR",
      borrower: entry.borrowerName ?? "—",
      connectionId: entry.connectionId,
      loan: loanLabel(entry.loanId),
      loanId: entry.loanId,
      reference: entry.reference ?? "—",
      payment: "—",
      settlement: "SETTLED",
      status: "POSTED",
    };
  });
  const total = data?.totalAmount ?? 0,
    count = data?.totalElements ?? 0,
    pages = Math.max(1, data?.totalPages ?? 1);
  function updatePage(next) {
    setPaging((current) => ({ ...current, scope: filterScope, page: next }));
  }
  function sortHeading(key, title) {
    if (!sortable.has(key)) return <th key={key}>{title}</th>;
    return (
      <th
        key={key}
        aria-sort={
          sort.key === key
            ? sort.direction === "asc"
              ? "ascending"
              : "descending"
            : "none"
        }
      >
        <button
          type="button"
          onClick={() =>
            setSort((current) => ({
              key,
              direction:
                current.key === key && current.direction === "asc"
                  ? "desc"
                  : "asc",
            }))
          }
        >
          {title}
          <span className="ref-sort">
            {sort.key === key ? (sort.direction === "asc" ? "↑" : "↓") : "♢"}
          </span>
        </button>
      </th>
    );
  }
  function filterField(key, title, values) {
    return (
      <label>
        {title}
        <select
          aria-label={title}
          value={draft[key]}
          onChange={(event) =>
            setDraft((current) => ({ ...current, [key]: event.target.value }))
          }
        >
          <option value="">All {title.toLowerCase()}s</option>
          {values.map(([value, text]) => (
            <option key={value} value={value}>
              {text}
            </option>
          ))}
        </select>
      </label>
    );
  }
  function cell(row, key) {
    if (key === "loan" && row.loanId != null)
      return <Link to={`/loans/${row.loanId}`}>{row.loan}</Link>;
    if (["settlement", "status"].includes(key))
      return <FinanceState value={row[key]} />;
    if (key === "executionFeeAmount") return row[key] == null ? "—" : <span className="finance-number" title="Included in the recorded purchase or settlement amount">{money(row[key])}</span>;
    if (["debit", "credit"].includes(key))
      return <span className="finance-number">{money(row[key])}</span>;
    return row[key];
  }
  return (
    <section className="ref-page finance-page ledger-reference">
      <header className="ref-heading">
        <h1>Settlement Ledger</h1>
        <span className="ledger-heading-divider" />
      </header>
      <LenderFunding key={organizationId} organizationId={organizationId}
        refreshKey={refreshKey} onApproved={() => setFundingRevision((revision) => revision + 1)} />
      <ReferenceMetrics
        metrics={[
          ["▱", "Total Debits", data ? money(total) : "—"],
          ["▱", "Total Credits", data ? money(total) : "—"],
          ["⇄", "Net Movement", "₹0.00"],
          ["▤", "Unsettled Items", "0 (₹0.00)"],
        ]}
      />
      <form
        className="ref-filters finance-filters"
        onSubmit={(event) => {
          event.preventDefault();
          setFilters({ ...draft });
        }}
      >
        {filterField("account", "Account", [
          ["rebate", accountFor("rebate")],
          ["fee", accountFor("fee")],
          ["buyin", accountFor("buyin")],
          ["funding", accountFor("lender funding")],
          ["clearing", accountFor("pledge")],
        ])}
        {filterField(
          "type",
          "Entry Type",
          options.types.map((type) => [type, label(type)]),
        )}
        {filterField(
          "connectionId",
          "Borrower",
          options.borrowers.map((b) => [b.id, b.name]),
        )}
        {filterField(
          "loanId",
          "Loan",
          options.loans.map((id) => [id, loanLabel(id)]),
        )}
        <button
          className="ref-clear"
          type="button"
          onClick={() => {
            setDraft(blank);
            setFilters(blank);
            setParams({});
          }}
        >
          Clear
        </button>
        <button className="ref-primary" type="submit">
          Apply
        </button>
      </form>
      {reference && (
        <div className="finance-reference-notice">
          Reference: {reference}{" "}
          <button
            className="ref-clear"
            type="button"
            onClick={() => setParams({})}
          >
            Show all entries
          </button>
        </div>
      )}
      {loading ? (
        <p className="ref-empty" role="status">
          Loading live ledger entries…
        </p>
      ) : error ? (
        <p className="live-error" role="alert">
          Live ledger unavailable: {error}
        </p>
      ) : (
        <ReferenceRegister
          columns={columns}
          rows={entries}
          expanded={expanded.scope === scope ? expanded.id : null}
          setExpanded={(update) =>
            setExpanded((current) => ({
              scope,
              id:
                typeof update === "function"
                  ? update(current.scope === scope ? current.id : null)
                  : update,
            }))
          }
          cell={cell}
          details={(row) => <JournalDetails row={row} />}
          sortHeading={sortHeading}
          className="finance-ledger-table"
        />
      )}
      <nav
        className="table-pagination"
        aria-label="Settlement ledger pagination"
      >
        <span>
          {loading
            ? "Loading…"
            : error
              ? "Ledger unavailable"
              : `${count ? page * paging.size + 1 : 0}–${Math.min((page + 1) * paging.size, count)} of ${count} entries`}
        </span>
        <label>
          Rows{" "}
          <select
            aria-label="Settlement ledger rows per page"
            value={paging.size}
            onChange={(event) =>
              setPaging({
                scope: filterScope,
                page: 0,
                size: Number(event.target.value),
              })
            }
          >
            {[10, 25, 50, 100].map((size) => (
              <option key={size}>{size}</option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={loading || !!error || page === 0}
          onClick={() => updatePage(0)}
        >
          First
        </button>
        <button
          type="button"
          disabled={loading || !!error || page === 0}
          onClick={() => updatePage(page - 1)}
        >
          Previous
        </button>
        <span>
          {loading ? "Loading page…" : `Page ${page + 1} of ${pages}`}
        </span>
        <button
          type="button"
          disabled={loading || !!error || page + 1 >= pages}
          onClick={() => updatePage(page + 1)}
        >
          Next
        </button>
        <button
          type="button"
          disabled={loading || !!error || page + 1 >= pages}
          onClick={() => updatePage(pages - 1)}
        >
          Last
        </button>
      </nav>
    </section>
  );
}
