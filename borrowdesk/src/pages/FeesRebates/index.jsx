import { Fragment, useMemo, useRef, useState } from "react";
import {
  Link,
  useLocation,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import {
  ChevronDown,
  ChevronRight,
  Download,
  ExternalLink,
  RefreshCw,
} from "lucide-react";
import useFeesData from "../../hooks/useFeesData";
import {
  buildFeeRecords,
  feeRecordPath,
  relatedFeeHistory,
} from "../../utils/feeRecords";
import { billingApi } from "../../api/billingApi";
import "./FeeDesk.css";
const money = (v, c = "INR") =>
  v == null
    ? "—"
    : new Intl.NumberFormat("en-IN", {
        style: "currency",
        currency: c,
        maximumFractionDigits: 2,
      }).format(v);
const number = (v) =>
  v == null
    ? "—"
    : Number(v).toLocaleString("en-IN", { maximumFractionDigits: 4 });
const date = (v) =>
  v
    ? new Date(v).toLocaleDateString("en-GB", { timeZone: "Asia/Kolkata" })
    : "—";
const kind = (r) => (r.kind === "REBATE" ? "Cash rebate" : "Lending fee");
function Badge({ value }) {
  return (
    <span
      className={`fd-badge ${/PAID|SETTLED|RECORDED/.test(value) ? "good" : /FAILED|REJECT|OVERDUE/.test(value) ? "bad" : "warn"}`}
    >
      {value ?? "Not recorded"}
    </span>
  );
}
function Pagination({ page, pages, total, onPage }) {
  return (
    <div
      className="fd-pagination"
      role="navigation"
      aria-label="Fees pagination"
    >
      <span>
        {total ? `${page * 10 + 1}–${Math.min((page + 1) * 10, total)}` : "0"}{" "}
        of {total}
      </span>
      <button disabled={!page} onClick={() => onPage(page - 1)}>
        Previous
      </button>
      <span>
        Page {page + 1} of {pages}
      </span>
      <button disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>
        Next
      </button>
    </div>
  );
}
function Card({ title, children }) {
  return (
    <section className="fd-card">
      <h2>{title}</h2>
      {children}
    </section>
  );
}
function Fields({ rows }) {
  return (
    <dl className="fd-fields">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}
function RecordOverview({ record: r }) {
  return (
    <div className="fd-cards">
      <Card title="Record details">
        <Fields
          rows={[
            ["Record", r.label],
            ["Loan", `LN-${String(r.loanId).padStart(6, "0")}`],
            ["Security", r.symbol],
            ["Lender", r.lender],
            [
              "Type",
              `${kind(r)} · ${r.category === "accruals" ? "Daily accrual" : "Settlement"}`,
            ],
            ["Date", date(r.date)],
            ["Outstanding shares", number(r.loan?.outstandingQuantity)],
            ["Status", <Badge value={r.status} />],
          ]}
        />
      </Card>
      <Card title="Calculation context">
        <Fields
          rows={[
            ["Recorded amount", money(r.amount, r.currency)],
            ["Historical base value", money(r.baseValue, r.currency)],
            [
              "Annual rate",
              r.rate == null ? "—" : `${number(r.rate * 10000)} bps`,
            ],
            ["MSLA day basis", r.dayBasis ? `Actual/${r.dayBasis}` : "—"],
            ["Period start", date(r.periodStart)],
            ["Period end", date(r.periodEnd)],
          ]}
        />
        <p className="fd-note">
          Amounts are recorded API values. Current MSLA terms are context;
          historical daily records remain authoritative.
        </p>
      </Card>
      <Card title="Settlement context">
        <Fields
          rows={[
            ["Linked bill / settlement", r.bill?.id ?? "Not issued"],
            ["Status", r.bill?.status ?? "Not issued"],
            ["Due / scheduled date", date(r.dueDate)],
            ["Paid date", date(r.bill?.paidAt)],
          ]}
        />
      </Card>
    </div>
  );
}
function DetailsTable({ headers, children }) {
  return (
    <div className="fd-table-scroll">
      <table>
        <thead>
          <tr>
            {headers.map((h) => (
              <th key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}
function DailyHistory({ record, data, compact = false }) {
  const [page, setPage] = useState(0);
  const rows = relatedFeeHistory(record, data),
    pages = Math.max(1, Math.ceil(rows.length / (compact ? 4 : 10))),
    current = Math.min(page, pages - 1);
  return (
    <Card title="Daily accrual history">
      <DetailsTable
        headers={["Date", "Type", "Historical base", "Rate (bps)", "Amount"]}
      >
        {rows
          .slice(
            current * (compact ? 4 : 10),
            (current + 1) * (compact ? 4 : 10),
          )
          .map((r) => (
            <tr key={r.id}>
              <td>{date(r.accrualDate)}</td>
              <td>{r.type}</td>
              <td className="num">{money(r.baseValue, record.currency)}</td>
              <td className="num">{number(r.rate * 10000)}</td>
              <td className="num">{money(r.amount, record.currency)}</td>
            </tr>
          ))}
        {!rows.length && (
          <tr>
            <td colSpan={5}>
              No daily accrual records are available for this period.
            </td>
          </tr>
        )}
      </DetailsTable>
      {!compact && (
        <Pagination
          page={current}
          pages={pages}
          total={rows.length}
          onPage={setPage}
        />
      )}
    </Card>
  );
}
function Settlements({ record, data }) {
  const records = [
    ...data.fees
      .filter((r) => String(r.loanId) === String(record.loanId))
      .map((r) => ({ ...r, label: `FEE-${r.id}`, amount: r.feeAmount })),
    ...data.rebates
      .filter((r) => String(r.loanId) === String(record.loanId))
      .map((r) => ({ ...r, label: `REB-${r.id}` })),
  ];
  return (
    <Card title="Loan bills and settlements">
      <DetailsTable
        headers={["Reference", "Period", "Amount", "Status", "Due / scheduled"]}
      >
        {records.map((r) => (
          <tr key={r.label}>
            <td>{r.label}</td>
            <td>
              {date(r.periodStart)} – {date(r.periodEnd)}
            </td>
            <td className="num">{money(r.amount, record.currency)}</td>
            <td>
              <Badge value={r.status} />
            </td>
            <td>{date(r.dueDate ?? r.nextPaymentDate)}</td>
          </tr>
        ))}
        {!records.length && (
          <tr>
            <td colSpan={5}>No settlements recorded for this loan.</td>
          </tr>
        )}
      </DetailsTable>
    </Card>
  );
}
function Payments({ record, data }) {
  const feeIds = new Set(
    data.fees
      .filter((f) => String(f.loanId) === String(record.loanId))
      .map((f) => String(f.id)),
  );
  const records = [
    ...data.payments
      .filter((r) => String(r.loanId) === String(record.loanId))
      .map((r) => ({
        ...r,
        key: `rebate:${r.id}`,
        label: `REB-PAY-${r.id}`,
        type: "Cash rebate",
        date: r.paymentDate,
        reference: `REB-${r.rebateId}`,
        status: "PAID",
      })),
    ...(data.feePayments ?? [])
      .filter((r) => feeIds.has(String(r.feeId)))
      .map((r) => ({
        ...r,
        key: `fee:${r.id}`,
        label: `PAY-${r.id}`,
        type: "Fee payment request",
        date: r.confirmedAt ?? r.createdAt,
        reference: `FEE-${r.feeId}`,
      })),
  ];
  return (
    <Card title="Payment history">
      <DetailsTable
        headers={["Payment", "Type", "Date", "Amount", "Reference", "Status"]}
      >
        {records.map((r) => (
          <tr key={r.key}>
            <td>{r.label}</td>
            <td>{r.type}</td>
            <td>{date(r.date)}</td>
            <td className="num">{money(r.amount, record.currency)}</td>
            <td>{r.reference}</td>
            <td>
              <Badge value={r.status} />
            </td>
          </tr>
        ))}
        {!records.length && (
          <tr>
            <td colSpan={6}>No payment records returned for this loan.</td>
          </tr>
        )}
      </DetailsTable>
    </Card>
  );
}

function exportRecords(records) {
  const keys = [
    "label",
    "loanId",
    "symbol",
    "lender",
    "kind",
    "category",
    "date",
    "amount",
    "rate",
    "status",
  ];
  const text = [keys, ...records.map((r) => keys.map((k) => r[k] ?? ""))]
    .map((row) =>
      row.map((v) => `"${String(v).replaceAll('"', '""')}"`).join(","),
    )
    .join("\n");
  const url = URL.createObjectURL(new Blob([text], { type: "text/csv" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = "fees-rebates.csv";
  a.click();
  URL.revokeObjectURL(url);
}
export default function FeesRebates() {
  const { data, loading, error, refresh } = useFeesData();
  const records = useMemo(() => buildFeeRecords(data), [data]);
  const [params, setParams] = useSearchParams(),
    navigate = useNavigate();
  const [expanded, setExpanded] = useState(null);
  const search = params.get("q") ?? "",
    type = params.get("type") ?? "ALL";
  const lender = params.get("lender") ?? "",
    security = params.get("security") ?? "",
    status = params.get("status") ?? "",
    from = params.get("from") ?? "",
    to = params.get("to") ?? "";
  const filtered = records.filter(
    (r) =>
      (type === "ALL" || r.kind === type) &&
      (!lender || r.lender === lender) &&
      (!security || r.symbol === security) &&
      (!status || r.status === status) &&
      (!from || r.date.slice(0, 10) >= from) &&
      (!to || r.date.slice(0, 10) <= to) &&
      `${r.label} ${r.loanId} ${r.symbol} ${r.lender}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  const pages = Math.max(1, Math.ceil(filtered.length / 10)),
    page = Math.max(
      0,
      Math.min(Math.floor(Number(params.get("page"))) || 0, pages - 1),
    );
  const query = params.toString() ? `?${params}` : "";
  const update = (k, v) => {
    const next = new URLSearchParams(params);
    if (v) next.set(k, String(v));
    else next.delete(k);
    if (k !== "page") next.delete("page");
    setParams(next, { replace: true });
  };
  const daily = filtered.filter((r) => r.category === "accruals");
  const totals = Object.entries(
    daily.reduce((groups, r) => {
      (groups[r.currency] ??= []).push(r);
      return groups;
    }, {}),
  ).map(([currency, rows]) => ({
    currency,
    fee: rows
      .filter((r) => r.kind === "FEE")
      .reduce((n, r) => n + (r.amount ?? 0), 0),
    rebate: rows
      .filter((r) => r.kind === "REBATE")
      .reduce((n, r) => n + (r.amount ?? 0), 0),
  }));
  const amounts = (key) =>
    totals.length
      ? totals
          .map((t) =>
            money(key === "net" ? t.fee - t.rebate : t[key], t.currency),
          )
          .join(" / ")
      : "—";
  const options = (key) =>
    [...new Set(records.map((r) => r[key]).filter(Boolean))].sort();
  return (
    <main className="fee-desk">
      <header className="fd-heading">
        <div>
          <h1>Fees &amp; Rebates</h1>
          <p>Recorded borrowing fees, cash rebates and settlements.</p>
        </div>
        <div className="fd-actions">
          <button onClick={refresh} disabled={loading}>
            <RefreshCw size={14} />
            Refresh
          </button>
          <button onClick={() => exportRecords(filtered)}>
            <Download size={14} />
            Export
          </button>
        </div>
      </header>
      <div className="fd-metrics">
        <span>
          Recorded fees <b>{amounts("fee")}</b>
        </span>
        <span>
          Recorded rebates <b>{amounts("rebate")}</b>
        </span>
        <span>
          Net accrued <b>{amounts("net")}</b>
        </span>
        <span>
          Due fee bills{" "}
          <b>
            {
              filtered.filter(
                (r) => r.category === "fees" && r.status === "DUE",
              ).length
            }
          </b>
        </span>
        <span>
          Payment requests{" "}
          <b>
            {
              data.feePayments.filter(
                (r) =>
                  !["CONFIRMED", "PAID", "REJECTED", "FAILED"].includes(
                    r.status,
                  ),
              ).length
            }
          </b>
        </span>
        <span>
          Matching records <b>{filtered.length}</b>
        </span>
      </div>
      <div className="fd-filters">
        <label>
          Search
          <input
            value={search}
            placeholder="Record, loan, security or lender"
            onChange={(e) => update("q", e.target.value)}
          />
        </label>
        <label>
          Type
          <select value={type} onChange={(e) => update("type", e.target.value)}>
            <option value="ALL">All types</option>
            <option value="FEE">Lending fees</option>
            <option value="REBATE">Cash rebates</option>
          </select>
        </label>
        {[
          ["lender", "Lender", lender],
          ["symbol", "Security", security],
          ["status", "Status", status],
        ].map(([key, label, value]) => (
          <label key={key}>
            {label}
            <select
              value={value}
              onChange={(e) =>
                update(key === "symbol" ? "security" : key, e.target.value)
              }
            >
              <option value="">All {label.toLowerCase()}s</option>
              {options(key).map((v) => (
                <option key={v}>{v}</option>
              ))}
            </select>
          </label>
        ))}
        <label>
          From
          <input
            type="date"
            value={from}
            max={to || undefined}
            onChange={(e) => update("from", e.target.value)}
          />
        </label>
        <label>
          To
          <input
            type="date"
            value={to}
            min={from || undefined}
            onChange={(e) => update("to", e.target.value)}
          />
        </label>
        <button onClick={() => setParams({})}>Clear</button>
      </div>
      {error && (
        <p role="alert" className="fd-error">
          {error}
        </p>
      )}
      <section className="fd-register">
        <DetailsTable
          headers={[
            "",
            "Record",
            "Loan",
            "Security",
            "Lender",
            "Type",
            "Date / period end",
            "Base value",
            "Rate (bps)",
            "Amount",
            "Due date",
            "Status",
            "",
          ]}
        >
          {filtered.slice(page * 10, (page + 1) * 10).map((r) => (
            <Fragment key={r.key}>
              <tr
                className={expanded === r.key ? "fd-selected" : ""}
                onClick={(e) => {
                  if (!e.target.closest("a,button"))
                    navigate(feeRecordPath(r) + query);
                }}
              >
                <td>
                  <button
                    aria-label={`${expanded === r.key ? "Collapse" : "Expand"} ${r.label}`}
                    aria-expanded={expanded === r.key}
                    onClick={() =>
                      setExpanded(expanded === r.key ? null : r.key)
                    }
                  >
                    {expanded === r.key ? (
                      <ChevronDown size={14} />
                    ) : (
                      <ChevronRight size={14} />
                    )}
                  </button>
                </td>
                <td>
                  <Link to={feeRecordPath(r) + query}>{r.label}</Link>
                </td>
                <td>LN-{String(r.loanId).padStart(6, "0")}</td>
                <td>{r.symbol}</td>
                <td>{r.lender}</td>
                <td>
                  {kind(r)}
                  <small>
                    {r.category === "accruals" ? "Daily accrual" : "Settlement"}
                  </small>
                </td>
                <td>{date(r.date)}</td>
                <td className="num">{money(r.baseValue, r.currency)}</td>
                <td className="num">
                  {r.rate == null ? "—" : number(r.rate * 10000)}
                </td>
                <td className="num">{money(r.amount, r.currency)}</td>
                <td>{date(r.dueDate)}</td>
                <td>
                  <Badge value={r.status} />
                </td>
                <td>
                  <Link
                    to={feeRecordPath(r) + query}
                    aria-label={`Open workspace ${r.label}`}
                  >
                    <ExternalLink size={14} />
                  </Link>
                </td>
              </tr>
              {expanded === r.key && (
                <tr className="fd-inline">
                  <td colSpan={13}>
                    <div className="fd-inline-heading">
                      <b>
                        {r.label} / {r.symbol}
                      </b>
                      <Link
                        className="fd-primary"
                        to={feeRecordPath(r) + query}
                      >
                        Open workspace <ExternalLink size={13} />
                      </Link>
                    </div>
                    <div className="fd-inline-grid">
                      <Card title="Calculation context">
                        <Fields
                          rows={[
                            ["Historical base", money(r.baseValue, r.currency)],
                            [
                              "Annual rate",
                              r.rate == null
                                ? "—"
                                : `${number(r.rate * 10000)} bps`,
                            ],
                            [
                              "Current day basis",
                              r.dayBasis ? `Actual/${r.dayBasis}` : "—",
                            ],
                            ["Recorded amount", money(r.amount, r.currency)],
                          ]}
                        />
                        <p className="fd-formula">
                          Daily accrual = base × annual rate ÷ day basis
                        </p>
                      </Card>
                      <DailyHistory
                        key={r.key}
                        compact
                        record={r}
                        data={data}
                      />
                      <Card title="Settlement context">
                        <Fields
                          rows={[
                            ["Reference", r.bill?.id ?? "Not issued"],
                            ["Status", r.bill?.status ?? "Not issued"],
                            ["Due / scheduled", date(r.dueDate)],
                            ["Paid date", date(r.bill?.paidAt)],
                          ]}
                        />
                      </Card>
                      <Payments record={r} data={data} />
                    </div>
                  </td>
                </tr>
              )}
            </Fragment>
          ))}
          {!filtered.length && (
            <tr>
              <td colSpan={13}>
                {loading
                  ? "Loading fees and rebates…"
                  : "No recorded entries match this view."}
              </td>
            </tr>
          )}
        </DetailsTable>
        <Pagination
          page={page}
          pages={pages}
          total={filtered.length}
          onPage={(value) => update("page", value)}
        />
      </section>
    </main>
  );
}
export function FeeWorkspace() {
  const { category, id } = useParams();
  const { search } = useLocation();
  const { data, loading, error, refresh } = useFeesData();
  const records = useMemo(() => buildFeeRecords(data), [data]);
  const record = records.find(
    (r) => r.category === category && String(r.id) === id,
  );
  const [tab, setTab] = useState("Overview");
  const [busy, setBusy] = useState(false),
    [notice, setNotice] = useState("");
  const dialog = useRef(null),
    requestKey = useRef(null);
  const bill = record?.kind === "FEE" ? record.bill : null;
  const payable = bill?.status === "DUE";
  async function pay() {
    if (busy || !bill) return;
    setBusy(true);
    setNotice("");
    if (requestKey.current?.billId !== bill.id)
      requestKey.current = { billId: bill.id, key: crypto.randomUUID() };
    try {
      await billingApi.payFee(bill.id, requestKey.current.key);
      setNotice(
        "Payment request accepted. Refresh to check confirmation; acceptance does not mean settlement.",
      );
      dialog.current.close();
      refresh();
    } catch (e) {
      setNotice(
        `${e.message}. If the outcome is uncertain, refresh before retrying; the same request key is retained.`,
      );
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="fee-desk fd-workspace">
      <Link className="fd-back" to={"/fees-rebates" + search}>
        ← Back to Fees &amp; Rebates
      </Link>
      {loading ? (
        <p role="status">Loading record…</p>
      ) : error ? (
        <p role="alert" className="fd-error">
          {error}
        </p>
      ) : !record ? (
        <section className="fd-card">
          <h1>Record not found</h1>
          <p>This record is unavailable for the configured organization.</p>
        </section>
      ) : (
        <>
          <header className="fd-heading">
            <div>
              <p>Fees &amp; Rebates / {record.label}</p>
              <h1>
                {record.label} / {record.symbol} <Badge value={record.status} />
              </h1>
              <p>
                {record.lender} · Loan {record.loanId} · {kind(record)}
              </p>
            </div>
            <div className="fd-actions">
              <button onClick={refresh}>Refresh</button>
              <button onClick={() => exportRecords([record])}>
                Export statement
              </button>
              {payable && (
                <button
                  className="fd-primary"
                  onClick={() => dialog.current.showModal()}
                >
                  Request fee payment
                </button>
              )}
            </div>
          </header>
          <div className="fd-metrics">
            <span>
              Historical base <b>{money(record.baseValue, record.currency)}</b>
            </span>
            <span>
              Recorded amount <b>{money(record.amount, record.currency)}</b>
            </span>
            <span>
              Annual rate{" "}
              <b>
                {record.rate == null
                  ? "—"
                  : `${number(record.rate * 10000)} bps`}
              </b>
            </span>
            <span>
              Date <b>{date(record.date)}</b>
            </span>
            <span>
              Due / scheduled <b>{date(record.dueDate)}</b>
            </span>
            <span>
              Day basis{" "}
              <b>{record.dayBasis ? `Actual/${record.dayBasis}` : "—"}</b>
            </span>
          </div>
          <nav className="fd-tabs" role="tablist" aria-label="Record workspace">
            {[
              "Overview",
              "Daily accruals",
              "Settlements",
              "Payment history",
            ].map((name) => (
              <button
                key={name}
                role="tab"
                aria-selected={tab === name}
                onClick={() => setTab(name)}
              >
                {name}
              </button>
            ))}
          </nav>
          {notice && <p role="status">{notice}</p>}
          <div className="fd-workspace-body">
            {tab === "Overview" ? (
              <>
                <RecordOverview record={record} />
                <div className="fd-workspace-grid">
                  <DailyHistory key={record.key} record={record} data={data} />
                  <div className="fd-side-stack">
                    <Settlements record={record} data={data} />
                    <Payments record={record} data={data} />
                  </div>
                </div>
              </>
            ) : tab === "Daily accruals" ? (
              <DailyHistory key={record.key} record={record} data={data} />
            ) : tab === "Settlements" ? (
              <Settlements record={record} data={data} />
            ) : (
              <Payments record={record} data={data} />
            )}
          </div>
          <dialog
            ref={dialog}
            onCancel={(e) => {
              if (busy) e.preventDefault();
            }}
          >
            <h2>Request fee payment</h2>
            <p>
              Bill FEE-{bill?.id} · {money(bill?.feeAmount, record.currency)}
            </p>
            <p>This sends a real payment request to the borrower service.</p>
            {notice && <p role="alert">{notice}</p>}
            <div className="fd-actions">
              <button disabled={busy} onClick={() => dialog.current.close()}>
                Back
              </button>
              <button className="fd-primary" disabled={busy} onClick={pay}>
                {busy ? "Submitting…" : "Confirm payment request"}
              </button>
            </div>
          </dialog>
        </>
      )}
    </main>
  );
}
