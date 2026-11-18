import { useEffect, useRef, useState } from "react";
import { get, orgUrl, unwrapRows } from "../../api/apiClient";
import { fundingApi } from "../../api/fundingApi";
import "./AdminDepositRequests.css";
const money = (value) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(
    Number(value),
  );
export default function AdminDepositRequests({ onApproved }) {
  const [admin, setAdmin] = useState(false),
    [open, setOpen] = useState(false),
    [records, setRecords] = useState([]),
    [page, setPage] = useState(0),
    [pages, setPages] = useState(1),
    [selected, setSelected] = useState(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [revision, setRevision] = useState(0),
    [loading, setLoading] = useState(false);
  const sending = useRef(false);
  useEffect(() => {
    let live = true;
    get(orgUrl("/admin-access"))
      .then((a) => {
        if (live) setAdmin(a.admin === true);
      })
      .catch(() => {
        if (live) setAdmin(false);
      });
    return () => {
      live = false;
    };
  }, []);
  useEffect(() => {
    if (!open) return;
    let live = true;
    setLoading(true);
    get(orgUrl(`/deposits?page=${page}&size=20`))
      .then((data) => {
        if (live) {
          setRecords(unwrapRows(data));
          setPages(data.totalPages || 1);
          setError("");
        }
      })
      .catch((e) => {
        if (live) setError(e.message);
      })
      .finally(() => {
        if (live) setLoading(false);
      });
    return () => {
      live = false;
    };
  }, [open, page, revision]);
  async function accept() {
    if (sending.current || !selected) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      await fundingApi.approveDeposit(selected.id, {});
      setSelected(null);
      setRevision((v) => v + 1);
      await onApproved();
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }
  if (!admin) return null;
  return (
    <>
      <button
        className="funding-btn"
        type="button"
        onClick={() => {
          setOpen(true);
          setSelected(null);
          setError("");
        }}
      >
        Deposit requests
      </button>
      {open && (
        <div className="deposit-admin-backdrop">
          <section
            className="deposit-admin-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="deposit-admin-title"
          >
            <header>
              <div>
                <h2 id="deposit-admin-title">Deposit requests</h2>
                <p>Review incoming requests and accept confirmed deposits.</p>
              </div>
              <button
                aria-label="Close deposit requests"
                disabled={busy}
                onClick={() => setOpen(false)}
              >
                ×
              </button>
            </header>
            <button
              disabled={busy || loading}
              onClick={() => {
                setSelected(null);
                setRevision((v) => v + 1);
              }}
            >
              Refresh requests
            </button>
            {error && (
              <p className="deposit-admin-error" role="alert">
                {error}
              </p>
            )}
            <div className="deposit-admin-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Amount</th>
                    <th>Reference</th>
                    <th>Status</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {records.map((r) => (
                    <tr key={r.id}>
                      <td>DEP-{r.id}</td>
                      <td>{money(r.amount)}</td>
                      <td>{r.clientRequestId || r.client_request_id || "—"}</td>
                      <td>{r.status}</td>
                      <td>
                        {r.status === "PENDING" && (
                          <button
                            disabled={busy}
                            onClick={() => setSelected(r)}
                          >
                            Accept deposit #{r.id}
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                  {!records.length && (
                    <tr>
                      <td colSpan={5}>
                        {loading ? "Loading requests…" : "No deposit requests"}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <footer>
              <button
                disabled={busy || page === 0}
                onClick={() => {
                  setSelected(null);
                  setPage((p) => p - 1);
                }}
              >
                Previous
              </button>
              <span>
                Page {page + 1} of {pages}
              </span>
              <button
                disabled={busy || page + 1 >= pages}
                onClick={() => {
                  setSelected(null);
                  setPage((p) => p + 1);
                }}
              >
                Next
              </button>
            </footer>
            {selected && (
              <div className="deposit-admin-confirm">
                <b>
                  Accept {money(selected.amount)} for deposit #{selected.id}?
                </b>
                <p>
                  Confirm receipt before accepting. This credits the borrower’s
                  available cash immediately.
                </p>
                <button disabled={busy} onClick={() => setSelected(null)}>
                  Cancel acceptance
                </button>
                <button className="primary" disabled={busy} onClick={accept}>
                  {busy ? "Accepting…" : "Confirm deposit acceptance"}
                </button>
              </div>
            )}
          </section>
        </div>
      )}
    </>
  );
}
