import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { useDataSource } from "../hooks/useDataSource";
import {
  defaultsBuyinsApi,
  canRequestBuyin,
  requestPending,
} from "../api/defaultsBuyinsApi";
import LiveBuyinRequestDialog from "../components/LiveBuyinRequestDialog";
import {
  LiveBuyinExecution,
  LiveBuyinRecovery,
  LiveObligation,
} from "../components/DefaultsBuyinsLiveDetails";
import Topbar from "../components/Topbar";
import DefaultsBuyinsDialog from "../components/DefaultsBuyinsDialog";
import {
  caseDate,
  caseDay,
  defaultCases,
  number,
  rupees,
} from "../data/defaultsBuyinsData";
import "./DefaultsBuyinsPage.css";

const blankFilters = {
  borrower: "",
  search: "",
  trigger: "",
  status: "",
  deadline: "",
};
const columns = [
  ["id", "Default ID"],
  ["loanId", "Loan ID"],
  ["borrower", "Borrower"],
  ["security", "Security"],
  ["trigger", "Trigger"],
  ["quantity", "Qty due"],
  ["exposure", "Exposure (₹)"],
  ["deadline", "Deadline IST"],
  ["status", "Default state"],
];
const detailTabs = [
  "Case overview",
  "Buy-in execution",
  "Recovery & ledger",
  "Activity",
];
const isResolved = (record) => record.status === "Resolved";
function Badge({ children }) {
  const tone = /resolved|recovered|^sent$|posted/i.test(children)
    ? "good"
    : /in default|overdue/i.test(children)
      ? "risk"
      : /pending|progress|partial|awaiting/i.test(children)
        ? "warn"
        : "purple";
  return <span className={`db-badge db-${tone}`}>{children}</span>;
}
function Empty({ children }) {
  return <p className="db-empty">{children}</p>;
}
function Obligation({ record }) {
  if (record.live) return <LiveObligation record={record} />;
  return (
    <article className="db-card">
      <h3>Default &amp; obligation</h3>
      <dl>
        {[
          ["Trigger", record.trigger],
          ["Borrower", record.borrower],
          ["Loan", record.loanId],
          ["Source reference", record.reference],
          ["Required return", `${number(record.quantity)} ${record.security}`],
          ["Deadline (IST)", caseDate(record.deadline)],
        ].map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
      <p className="db-footnote">
        {record.status === "Draft"
          ? "Local draft — no default has been recorded."
          : "Sample obligation; verify the source before recording a default."}
      </p>
    </article>
  );
}
function Notices({ record }) {
  if (record.live)
    return (
      <article className="db-card">
        <h3>Notices &amp; deadlines</h3>
        <Empty>No notices are supplied by the current backend contract.</Empty>
        <p className="db-footnote">
          Notice preparation and delivery are not enabled in live mode.
        </p>
      </article>
    );
  return (
    <article className="db-card">
      <h3>
        Notices &amp; deadlines <span>({record.notices.length})</span>
      </h3>
      {record.notices.length ? (
        <div className="db-scroll">
          <table>
            <thead>
              <tr>
                <th>Notice / reference</th>
                <th>Recipient</th>
                <th>Sent IST</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {record.notices.map((item) => (
                <tr key={item.id}>
                  <td>
                    {item.type}
                    <small className="db-reference">{item.id}</small>
                    {item.message && (
                      <small className="db-notice-message">
                        {item.message}
                      </small>
                    )}
                  </td>
                  <td>{item.recipient}</td>
                  <td>{item.sent || "Not sent"}</td>
                  <td>
                    <Badge>{item.status}</Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <Empty>No notices linked to this case.</Empty>
      )}
      <div className="db-card-bottom">
        <span>Obligation deadline</span>
        <b>{caseDate(record.deadline)}</b>
      </div>
    </article>
  );
}
function Execution({ record, onManage }) {
  if (record.live)
    return (
      <LiveBuyinExecution
        record={record}
        onRequest={onManage}
        allowed={canRequestBuyin(record)}
      />
    );
  const order = record.order;
  const filled = order?.filled || 0;
  const percent = order ? Math.round((filled / order.quantity) * 100) : 0;
  return (
    <article className="db-card">
      <h3>Buy-in execution {order && <span>· {order.buyinId}</span>}</h3>
      {order ? (
        <>
          <div className="db-execution-summary">
            <div>
              <small>Status</small>
              <Badge>{order.status}</Badge>
            </div>
            <div>
              <small>Order qty</small>
              <b>{number(order.quantity)}</b>
            </div>
            <div>
              <small>Filled</small>
              <b>{number(filled)}</b>
            </div>
            <div>
              <small>Remaining</small>
              <b>{number(order.quantity - filled)}</b>
            </div>
          </div>
          <div className="db-scroll">
            <table>
              <thead>
                <tr>
                  <th>Order</th>
                  <th>Security</th>
                  <th>Side</th>
                  <th>Avg fill</th>
                  <th>Execution cost</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="db-reference">{order.id}</td>
                  <td>{record.security}</td>
                  <td>
                    <Badge>Buy</Badge>
                  </td>
                  <td>{filled ? rupees(order.averagePrice) : "—"}</td>
                  <td>{rupees(filled * order.averagePrice)}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div className="db-progress-row">
            <progress aria-label="Buy-in filled" max="100" value={percent} />
            <b>{percent}%</b>
          </div>
          {record.buyinDraft && (
            <p className="db-draft-note">
              Unsaved to market · draft for {number(record.buyinDraft.quantity)}{" "}
              remaining @ {rupees(record.buyinDraft.price)}
            </p>
          )}
          <div className="db-card-bottom">
            <span>
              Remaining estimate{" "}
              <b>{rupees((order.quantity - filled) * order.limitPrice)}</b>
            </span>
            {!isResolved(record) && (
              <button className="db-button" onClick={onManage}>
                Manage order
              </button>
            )}
          </div>
        </>
      ) : (
        <>
          <Empty>
            No buy-in order linked. Prepare a draft after reviewing this
            obligation.
          </Empty>
          {record.buyinDraft && (
            <p className="db-draft-note">
              Local draft · {number(record.buyinDraft.quantity)}{" "}
              {record.security} @ {rupees(record.buyinDraft.price)}
            </p>
          )}
          {!isResolved(record) && (
            <button className="db-button" onClick={onManage}>
              {record.buyinDraft ? "Edit buy-in draft" : "Prepare buy-in"}
            </button>
          )}
        </>
      )}
    </article>
  );
}
function Recovery({ record }) {
  if (record.live) return <LiveBuyinRecovery record={record} />;
  const order = record.order,
    filled = order?.filled || 0;
  return (
    <article className="db-card">
      <h3>Recovery &amp; settlement</h3>
      <table>
        <thead>
          <tr>
            <th>Description</th>
            <th className="db-num">Amount</th>
            <th>State</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>Executed buy-in</td>
            <td className="db-num">
              {rupees(filled * (order?.averagePrice || 0))}
            </td>
            <td>
              <Badge>{filled ? "Posted" : "Not started"}</Badge>
            </td>
          </tr>
          <tr>
            <td>Remaining estimate</td>
            <td className="db-num">
              {order
                ? rupees((order.quantity - filled) * order.limitPrice)
                : "—"}
            </td>
            <td>
              <Badge>{order ? "Pending" : "Not started"}</Badge>
            </td>
          </tr>
          <tr>
            <td>Realized slippage</td>
            <td className="db-num">
              {rupees(
                filled * ((order?.averagePrice || record.price) - record.price),
              )}
            </td>
            <td>
              <Badge>{filled ? "Execution only" : "Not started"}</Badge>
            </td>
          </tr>
          <tr>
            <td>Collateral realized</td>
            <td className="db-num">
              {isResolved(record) ? rupees(record.exposure) : rupees(0)}
            </td>
            <td>
              <Badge>{isResolved(record) ? "Recovered" : "Not started"}</Badge>
            </td>
          </tr>
        </tbody>
      </table>
      <div className="db-card-bottom">
        <b>Loan close-out</b>
        <Badge>{isResolved(record) ? "Resolved" : "Awaiting settlement"}</Badge>
      </div>
    </article>
  );
}
function Activity({ record, compact = false }) {
  if (compact)
    return (
      <div className="db-timeline">
        <b>Activity timeline</b>
        {record.activities.slice(-4).map((item, i) => (
          <span key={i}>
            <i />
            {record.live ? caseDate(item.time) : item.time}{" "}
            <strong>{item.event}</strong>
          </span>
        ))}
      </div>
    );
  return (
    <article className="db-card">
      <h3>Activity &amp; audit trail</h3>
      <table>
        <thead>
          <tr>
            <th>Time (IST)</th>
            <th>Event</th>
            <th>Reference / details</th>
          </tr>
        </thead>
        <tbody>
          {record.activities.map((item, i) => (
            <tr key={i}>
              <td>{record.live ? caseDate(item.time) : item.time}</td>
              <td>{item.event}</td>
              <td>{item.reference}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </article>
  );
}

export default function DefaultsBuyinsPage({ preview = false }) {
  const { mode, organizationId, refreshKey } = useDataSource();
  const live = mode === "live" && !preview;
  return (
    <DefaultsBuyinsWorkspace
      key={`${live}:${organizationId}`}
      live={live}
      organizationId={organizationId}
      refreshKey={refreshKey}
    />
  );
}
function DefaultsBuyinsWorkspace({ live, organizationId, refreshKey }) {
  const [records, setRecords] = useState(() =>
    live ? [] : structuredClone(defaultCases),
  );
  const [selectedId, setSelectedId] = useState(live ? null : "DEF-000018");
  const [loading, setLoading] = useState(live),
    [loadError, setLoadError] = useState(""),
    [reload, setReload] = useState(0);
  useEffect(() => {
    if (!live) return;
    const controller = new AbortController();
    let timer;
    async function load() {
      try {
        const rows = await defaultsBuyinsApi.snapshot(
          organizationId,
          controller.signal,
        );
        if (controller.signal.aborted) return;
        setRecords(rows);
        setSelectedId((current) =>
          current && rows.some((item) => item.id === current)
            ? current
            : (rows[0]?.id ?? null),
        );
        setLoadError("");
      } catch (error) {
        if (!controller.signal.aborted) setLoadError(error.message);
      } finally {
        if (!controller.signal.aborted) {
          setLoading(false);
          timer = setTimeout(load, 10000);
        }
      }
    }
    load();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [live, organizationId, refreshKey, reload]);
  function refreshLive() {
    setLoading(true);
    setReload((value) => value + 1);
  }

  const [tab, setTab] = useState("All cases"),
    [detailsTab, setDetailsTab] = useState("Case overview");
  const [draft, setDraft] = useState(blankFilters),
    [filters, setFilters] = useState(blankFilters);
  const [globalSearch, setGlobalSearch] = useState(""),
    [page, setPage] = useState(0);
  const [sort, setSort] = useState({ key: "id", direction: 1 }),
    [modal, setModal] = useState(null),
    [notice, setNotice] = useState("");
  const workspace = useRef(null);
  const [searchKey, setSearchKey] = useState(0);
  const defaults = records.filter(
      (item) => !item.order && !requestPending(item) && !isResolved(item),
    ),
    buyins = records.filter(
      (item) => (item.order || requestPending(item)) && !isResolved(item),
    ),
    resolved = records.filter(isResolved);
  const filtered = records
    .filter((item) => {
      if (
        (tab === "Defaults" && !defaults.includes(item)) ||
        (tab === "Buy-ins" && !buyins.includes(item)) ||
        (tab === "Resolved" && !isResolved(item))
      )
        return false;
      if (
        (filters.borrower && item.borrower !== filters.borrower) ||
        (filters.trigger && item.trigger !== filters.trigger) ||
        (filters.status && item.status !== filters.status)
      )
        return false;
      if (
        filters.deadline &&
        (live
          ? caseDay(item.deadline) !== filters.deadline
          : filters.deadline === "18 Nov"
            ? !item.deadline?.startsWith("2026-11-18")
            : !item.deadline?.startsWith("2026-11-19"))
      )
        return false;
      return (
        `${item.id} ${item.loanId} ${item.borrower} ${item.security} ${item.order?.buyinId || ""}`
          .toLowerCase()
          .includes(globalSearch.toLowerCase()) &&
        `${item.security} ${item.id} ${item.loanId}`
          .toLowerCase()
          .includes(filters.search.toLowerCase())
      );
    })
    .sort(
      (a, b) =>
        (typeof a[sort.key] === "number"
          ? a[sort.key] - b[sort.key]
          : String(a[sort.key]).localeCompare(String(b[sort.key]), undefined, {
              numeric: true,
            })) * sort.direction,
    );
  const pageSize = 8,
    pageCount = Math.max(1, Math.ceil(filtered.length / pageSize)),
    currentPage = Math.min(page, pageCount - 1),
    visible = filtered.slice(
      currentPage * pageSize,
      (currentPage + 1) * pageSize,
    );
  const selected = visible.find((item) => item.id === selectedId);
  function select(item) {
    setSelectedId(item.id);
    setDetailsTab("Case overview");
  }
  function filterChange(event) {
    setDraft((current) => ({
      ...current,
      [event.target.name]: event.target.value,
    }));
  }
  function reset() {
    setDraft(blankFilters);
    setFilters(blankFilters);
    setGlobalSearch("");
    setSearchKey((current) => current + 1);
    setPage(0);
  }
  function changeTab(value) {
    setTab(value);
    setPage(0);
  }
  function sortBy(key) {
    setSort((current) => ({
      key,
      direction: current.key === key ? -current.direction : 1,
    }));
    setPage(0);
  }
  function manage(item) {
    select(item);
    workspace.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }
  function save(values) {
    const time = new Date().toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      day: "2-digit",
      month: "short",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    });
    if (modal.kind === "default") {
      const sequence =
        Math.max(...records.map((item) => Number(item.id.split("-")[1]))) + 1;
      const id = `DEF-${String(sequence).padStart(6, "0")}`;
      setRecords((current) => [
        ...current,
        {
          id,
          loanId: values.loan.id,
          borrower: values.loan.borrower,
          security: values.loan.security,
          quantity: values.quantity,
          price: values.loan.price,
          exposure: values.loan.price * values.quantity,
          trigger: values.trigger,
          deadline: values.deadline,
          reference: values.reference,
          status: "Draft",
          recovery: "Not started",
          notices: [],
          order: null,
          activities: [
            {
              time,
              event: "Default draft saved locally",
              reference: values.reference,
            },
          ],
        },
      ]);
      setSelectedId(id);
      setSort({ key: "id", direction: 1 });
      setTab("All cases");
      reset();
      setPage(Math.floor(records.length / pageSize));
    } else {
      setRecords((current) =>
        current.map((item) =>
          item.id !== modal.record.id
            ? item
            : modal.kind === "notice"
              ? {
                  ...item,
                  notices: [
                    ...item.notices,
                    {
                      id: `DRAFT-${item.notices.length + 1}`,
                      type: values.notice,
                      recipient: item.borrower,
                      sent: null,
                      status: "Draft",
                      message: values.reference,
                    },
                  ],
                  activities: [
                    ...item.activities,
                    {
                      time,
                      event: "Notice draft saved locally",
                      reference: values.notice,
                    },
                  ],
                }
              : {
                  ...item,
                  buyinDraft: {
                    quantity: values.quantity,
                    price: values.price,
                  },
                  activities: [
                    ...item.activities,
                    {
                      time,
                      event: "Buy-in draft saved locally",
                      reference: `${number(values.quantity)} ${item.security} @ ${rupees(values.price)}`,
                    },
                  ],
                },
        ),
      );
    }
    setDetailsTab("Case overview");
    setModal(null);
    setNotice(
      "Draft saved in this page. No backend request was sent; reloading clears your changes.",
    );
  }
  function exportRows() {
    const rows = [
      [
        "Default ID",
        "Loan ID",
        "Borrower",
        "Security",
        "Trigger",
        "Quantity",
        "Exposure INR",
        "Deadline IST",
        "Status",
        "Buy-in ID",
      ],
      ...filtered.map((item) => [
        item.id,
        item.loanId,
        item.borrower,
        item.security,
        item.trigger,
        item.quantity,
        item.exposure,
        item.deadline,
        item.status,
        item.order?.buyinId || "",
      ]),
    ];
    const csv = rows
      .map((row) =>
        row
          .map((value) => `"${String(value ?? "").replaceAll('"', '""')}"`)
          .join(","),
      )
      .join("\r\n");
    const url = URL.createObjectURL(
        new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8;" }),
      ),
      link = document.createElement("a");
    link.href = url;
    link.download = live
      ? "lenddesk-defaults-buyins.csv"
      : "lenddesk-defaults-buyins-sample.csv";
    link.click();
    URL.revokeObjectURL(url);
  }
  const openBuyin = () => setModal({ kind: "buyin", record: selected });
  return (
    <div className="db-route">
      <Topbar
        key={searchKey}
        onSearch={(value) => {
          setGlobalSearch(value);
          setPage(0);
        }}
      />
      <section className="db-page">
        <header className="db-heading">
          <div>
            <div className="db-title">
              <h1>Defaults &amp; Buy-ins</h1>
              <span className="db-preview-chip">
                {live ? "LIVE DATA" : "SAMPLE DATA"}
              </span>
            </div>
            <p>Track failed obligations, recovery and replacement purchases</p>
          </div>
          <div className="db-actions">
            <button className="db-button" onClick={exportRows}>
              ↥ Export
            </button>
            {live ? (
              <>
                <Link className="db-button" to="/defaults-buyins/preview">
                  Sample preview
                </Link>
                <button
                  className="db-button"
                  disabled={loading}
                  onClick={refreshLive}
                >
                  {loading ? "Refreshing…" : "Refresh"}
                </button>
              </>
            ) : (
              <>
                <Link className="db-button" to="/defaults-buyins">
                  Live workspace
                </Link>
                <button
                  className="db-button db-primary"
                  onClick={() => setModal({ kind: "default" })}
                >
                  Record default
                </button>
              </>
            )}
          </div>
        </header>
        <p className="db-preview-banner">
          <span>ⓘ</span>{" "}
          {live
            ? "Defaults are reported by the lifecycle owner. Buy-in requests are queued for processing; execution reports do not confirm settlement."
            : "Frontend preview · Sample cases and local drafts. Notices and buy-in orders are not sent."}
        </p>
        {loadError && (
          <div className="db-load-error" role="alert">
            Unable to refresh live cases: {loadError}{" "}
            {records.length > 0 && "Showing the last loaded snapshot."}
            <button className="db-button" onClick={refreshLive}>
              Retry
            </button>
          </div>
        )}
        <section
          className="db-metrics"
          aria-label="Defaults and buy-ins summary"
        >
          {[
            [
              "Open defaults",
              live
                ? records.filter((item) => item.rawStatus === "OPEN").length
                : defaults.length,
            ],
            [
              "Buy-ins pending",
              live
                ? records.filter(
                    (item) =>
                      !isResolved(item) &&
                      (requestPending(item) ||
                        (item.order &&
                          ![
                            "COMPLETED",
                            "EXECUTED",
                            "SETTLED",
                            "FAILED",
                            "REJECTED",
                            "CANCELLED",
                          ].includes(item.order.status))),
                  ).length
                : buyins.length,
            ],
            [
              "Exposure at risk",
              live
                ? records.some((item) => item.exposure != null)
                  ? `${rupees(records.filter((item) => !isResolved(item)).reduce((sum, item) => sum + (item.exposure || 0), 0))}${records.some((item) => !isResolved(item) && item.exposure == null) ? " + unreported" : ""}`
                  : "Not reported"
                : rupees(
                    records
                      .filter((item) => !isResolved(item))
                      .reduce((sum, item) => sum + item.exposure, 0),
                  ),
            ],
            [
              live ? "Resolved cases" : "Recovered",
              live
                ? resolved.length
                : rupees(
                    resolved.reduce((sum, item) => sum + item.exposure, 0),
                  ),
            ],
          ].map(([label, value]) => (
            <div key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </div>
          ))}
        </section>
        <div className="db-tabs" role="group" aria-label="Filter cases">
          {[
            ["All cases", records.length],
            ["Defaults", defaults.length],
            ["Buy-ins", buyins.length],
            ["Resolved", resolved.length],
          ].map(([name, count]) => (
            <button
              key={name}
              aria-pressed={tab === name}
              className={tab === name ? "active" : ""}
              onClick={() => changeTab(name)}
            >
              {name} <b>{count}</b>
            </button>
          ))}
        </div>
        <form
          className="db-filters"
          onSubmit={(event) => {
            event.preventDefault();
            setFilters(draft);
            setPage(0);
          }}
        >
          <label>
            Borrower
            <select
              name="borrower"
              value={draft.borrower}
              onChange={filterChange}
            >
              <option value="">All borrowers</option>
              {[...new Set(records.map((item) => item.borrower))].map(
                (value) => (
                  <option key={value}>{value}</option>
                ),
              )}
            </select>
          </label>
          <label className="db-search-field">
            <span className="db-sr-only">Security or reference</span>
            <input
              name="search"
              placeholder="⌕ Search security, loan or default ID…"
              value={draft.search}
              onChange={filterChange}
            />
          </label>
          {[
            ["trigger", "Trigger", "All triggers"],
            ["status", "Status", "All states"],
          ].map(([name, label, placeholder]) => (
            <label key={name}>
              {label}
              <select name={name} value={draft[name]} onChange={filterChange}>
                <option value="">{placeholder}</option>
                {[...new Set(records.map((item) => item[name]))].map(
                  (value) => (
                    <option key={value}>{value}</option>
                  ),
                )}
              </select>
            </label>
          ))}
          <label>
            Deadline
            {live ? (
              <input
                type="date"
                aria-label="Deadline"
                name="deadline"
                value={draft.deadline}
                onChange={filterChange}
              />
            ) : (
              <select
                name="deadline"
                value={draft.deadline}
                onChange={filterChange}
              >
                <option value="">Any date</option>
                <option>18 Nov</option>
                <option>19 Nov</option>
              </select>
            )}
          </label>
          <button className="db-button" type="button" onClick={reset}>
            Reset
          </button>
          <button className="db-button db-primary" type="submit">
            Apply
          </button>
        </form>
        <div className="db-table-panel">
          <div className="db-scroll">
            <table className="db-main-table">
              <thead>
                <tr>
                  <th aria-label="Expand case" />
                  {columns.map(([key, label]) => (
                    <th
                      key={key}
                      aria-sort={
                        sort.key === key
                          ? sort.direction === 1
                            ? "ascending"
                            : "descending"
                          : "none"
                      }
                    >
                      <button className="db-sort" onClick={() => sortBy(key)}>
                        {label}{" "}
                        <span aria-hidden="true">
                          {sort.key === key
                            ? sort.direction === 1
                              ? "↑"
                              : "↓"
                            : "♢"}
                        </span>
                      </button>
                    </th>
                  ))}
                  <th>Buy-in ID</th>
                  <th>Recovery</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((item) => (
                  <tr
                    key={item.id}
                    className={selectedId === item.id ? "selected" : ""}
                    onClick={() => select(item)}
                  >
                    <td>
                      <button
                        className="db-expand"
                        aria-label={`${selectedId === item.id ? "Collapse" : "Expand"} ${item.id}`}
                        aria-expanded={selectedId === item.id}
                        onClick={(event) => {
                          event.stopPropagation();
                          setSelectedId((current) =>
                            current === item.id ? null : item.id,
                          );
                        }}
                      >
                        {selectedId === item.id ? "⌄" : "›"}
                      </button>
                    </td>
                    {columns.map(([key]) => (
                      <td
                        key={key}
                        className={
                          ["quantity", "exposure"].includes(key) ? "db-num" : ""
                        }
                      >
                        {key === "id" || key === "loanId" ? (
                          <button
                            className="db-link"
                            onClick={() => manage(item)}
                          >
                            {item[key]}
                          </button>
                        ) : key === "status" ? (
                          <Badge>{item.status}</Badge>
                        ) : key === "quantity" || key === "exposure" ? (
                          number(item[key])
                        ) : key === "deadline" ? (
                          caseDate(item.deadline)
                        ) : (
                          item[key]
                        )}
                      </td>
                    ))}
                    <td>
                      {item.order ? (
                        <button
                          className="db-link"
                          onClick={(event) => {
                            event.stopPropagation();
                            select(item);
                            setDetailsTab("Buy-in execution");
                          }}
                        >
                          {item.order.buyinId}
                        </button>
                      ) : item.buyinDraft ? (
                        <Badge>Draft</Badge>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td>
                      {item.recovery === "Not started" ? (
                        "—"
                      ) : (
                        <Badge>{item.recovery}</Badge>
                      )}
                    </td>
                    <td>
                      <button className="db-link" onClick={() => manage(item)}>
                        Manage
                      </button>
                    </td>
                  </tr>
                ))}
                {!visible.length && (
                  <tr>
                    <td colSpan={13}>
                      <Empty>
                        {loading
                          ? "Loading live cases…"
                          : loadError
                            ? "Live cases could not be loaded. Use Retry above."
                            : "No cases match your filters. Try Reset or another tab."}
                      </Empty>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          <footer className="db-table-footer">
            <span>
              Showing {filtered.length ? currentPage * pageSize + 1 : 0}–
              {Math.min((currentPage + 1) * pageSize, filtered.length)} of{" "}
              {filtered.length}
            </span>
            <div>
              <button
                className="db-button"
                disabled={currentPage === 0}
                onClick={() => setPage(currentPage - 1)}
              >
                Previous
              </button>
              {Array.from({ length: pageCount }, (_, i) => (
                <button
                  key={i}
                  className={`db-button ${currentPage === i ? "db-primary" : ""}`}
                  aria-label={`Page ${i + 1}`}
                  aria-current={currentPage === i ? "page" : undefined}
                  onClick={() => setPage(i)}
                >
                  {i + 1}
                </button>
              ))}
              <button
                className="db-button"
                disabled={currentPage + 1 >= pageCount}
                onClick={() => setPage(currentPage + 1)}
              >
                Next
              </button>
            </div>
          </footer>
        </div>
        {selected ? (
          <section
            ref={workspace}
            className="db-case-workspace"
            aria-label={`Case details ${selected.id}`}
          >
            <header className="db-case-heading">
              <div>
                <h2>
                  {selected.id} / {selected.security}
                </h2>
                <Badge>{selected.status}</Badge>
                <span>
                  {selected.borrower} · {selected.loanId}
                </span>
              </div>
              <div className="db-actions">
                <button
                  className="db-button"
                  disabled={live || isResolved(selected)}
                  title={
                    live
                      ? "Notice delivery is not supported by this backend"
                      : undefined
                  }
                  onClick={() => setModal({ kind: "notice", record: selected })}
                >
                  Prepare notice
                </button>
                <button
                  className="db-button db-primary"
                  disabled={
                    live ? !canRequestBuyin(selected) : isResolved(selected)
                  }
                  onClick={openBuyin}
                >
                  {live
                    ? requestPending(selected)
                      ? "Buy-in pending"
                      : selected.request?.status === "FAILED" ||
                          selected.request?.status === "REJECTED"
                        ? "Retry buy-in"
                        : "Request buy-in"
                    : selected.order
                      ? "Manage buy-in"
                      : selected.buyinDraft
                        ? "Edit buy-in draft"
                        : "Create buy-in"}
                </button>
                <button
                  className="db-button"
                  onClick={() => setDetailsTab("Activity")}
                >
                  View activity
                </button>
                <button
                  className="db-close"
                  aria-label="Collapse case details"
                  onClick={() => setSelectedId(null)}
                >
                  ⌃
                </button>
              </div>
            </header>
            <div className="db-linked-strip">
              <span>
                ⌁ Source <b>{selected.reference}</b>
              </span>
              <span>
                Due quantity <b>{number(selected.quantity)}</b>
              </span>
              <span>
                Deadline IST <b>{caseDate(selected.deadline)}</b>
              </span>
              <span>
                Exposure <b>{rupees(selected.exposure)}</b>
              </span>
            </div>
            <div
              className="db-tabs db-detail-tabs"
              role="tablist"
              aria-label="Case detail sections"
            >
              {detailTabs.map((name) => (
                <button
                  key={name}
                  role="tab"
                  id={`db-tab-${detailTabs.indexOf(name)}`}
                  aria-controls="db-detail-panel"
                  aria-selected={detailsTab === name}
                  tabIndex={detailsTab === name ? 0 : -1}
                  onKeyDown={(event) => {
                    if (
                      ["ArrowLeft", "ArrowRight", "Home", "End"].includes(
                        event.key,
                      )
                    ) {
                      event.preventDefault();
                      const index =
                        event.key === "Home"
                          ? 0
                          : event.key === "End"
                            ? detailTabs.length - 1
                            : (detailTabs.indexOf(name) +
                                (event.key === "ArrowRight" ? 1 : -1) +
                                detailTabs.length) %
                              detailTabs.length;
                      setDetailsTab(detailTabs[index]);
                      document.getElementById(`db-tab-${index}`)?.focus();
                    }
                  }}
                  className={detailsTab === name ? "active" : ""}
                  onClick={() => setDetailsTab(name)}
                >
                  {name}
                </button>
              ))}
            </div>
            <div
              id="db-detail-panel"
              role="tabpanel"
              aria-labelledby={`db-tab-${detailTabs.indexOf(detailsTab)}`}
              className={
                detailsTab === "Case overview"
                  ? "db-details-grid"
                  : "db-details-focus"
              }
            >
              {detailsTab === "Case overview" && (
                <>
                  <Obligation record={selected} />
                  <Notices record={selected} />
                  <Execution record={selected} onManage={openBuyin} />
                  <Recovery record={selected} />
                </>
              )}
              {detailsTab === "Buy-in execution" && (
                <>
                  <Execution record={selected} onManage={openBuyin} />
                  <Obligation record={selected} />
                </>
              )}
              {detailsTab === "Recovery & ledger" && (
                <>
                  <Recovery record={selected} />
                  <article className="db-card">
                    <h3>Settlement checks</h3>
                    <dl>
                      <div>
                        <dt>Default reference</dt>
                        <dd>{selected.id}</dd>
                      </div>
                      <div>
                        <dt>Executed quantity</dt>
                        <dd>
                          {live ? (
                            "Owner report required"
                          ) : (
                            <>
                              {number(selected.order?.filled || 0)} /{" "}
                              {number(selected.quantity)}
                            </>
                          )}
                        </dd>
                      </div>
                      <div>
                        <dt>Recovery state</dt>
                        <dd>
                          <Badge>{selected.recovery}</Badge>
                        </dd>
                      </div>
                    </dl>
                    <p className="db-footnote">
                      Close-out entries depend on confirmed settlement.
                      Remaining estimates are not posted ledger movements.
                    </p>
                  </article>
                </>
              )}
              {detailsTab === "Activity" && <Activity record={selected} />}
            </div>
            {detailsTab !== "Activity" && (
              <Activity record={selected} compact />
            )}
          </section>
        ) : (
          <div className="db-no-selection">
            Select a case to review its obligation, notices, buy-in execution
            and recovery.
          </div>
        )}
        {notice && (
          <div className="db-notice" role="status">
            {notice}
            <button
              className="db-close"
              aria-label="Dismiss notification"
              onClick={() => setNotice("")}
            >
              ×
            </button>
          </div>
        )}
        {modal && live && (
          <LiveBuyinRequestDialog
            record={modal.record}
            organizationId={organizationId}
            onClose={() => setModal(null)}
            onSubmitted={(request) => {
              setRecords((current) =>
                current.map((item) =>
                  item.backendId === modal.record.backendId
                    ? {
                        ...item,
                        request,
                        requests: [request, ...item.requests],
                      }
                    : item,
                ),
              );
              setModal(null);
              setNotice(
                `Buy-in request #${request.id}: ${request.status}. Waiting for the lifecycle owner; execution is not yet confirmed.`,
              );
              refreshLive();
            }}
          />
        )}
        {modal && !live && (
          <DefaultsBuyinsDialog
            kind={modal.kind}
            record={modal.record}
            onClose={() => setModal(null)}
            onSave={save}
          />
        )}
      </section>
    </div>
  );
}
