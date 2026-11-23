import { useEffect, useRef, useState } from "react";
import { lenderGet, lenderPost } from "../api/lendDeskApi";
import "./AdminInventoryReceipt.css";

const money = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" });

export default function LenderFunding({ organizationId, refreshKey, onApproved }) {
  const org = `/api/v1/organizations/${organizationId}`;
  const endpoint = `${org}/lenddesk/funding`;
  const [balance, setBalance] = useState(null);
  const [balanceError, setBalanceError] = useState("");
  const [admin, setAdmin] = useState(false);
  const [modal, setModal] = useState(null);
  const [amount, setAmount] = useState("");
  const [key, setKey] = useState("");
  const [page, setPage] = useState(0);
  const [deposits, setDeposits] = useState(null);
  const [version, setVersion] = useState(0);
  const [selected, setSelected] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const sending = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    lenderGet(`${org}/admin-access`, controller.signal)
      .then((access) => setAdmin(access.admin === true))
      .catch(() => {});
    return () => controller.abort();
  }, [org]);

  useEffect(() => {
    const controller = new AbortController();
    lenderGet(endpoint, controller.signal)
      .then((value) => {
        setBalance(value);
        setBalanceError("");
      })
      .catch((failure) => {
        if (!controller.signal.aborted) setBalanceError(failure.message);
      });
    return () => controller.abort();
  }, [endpoint, refreshKey, version]);

  useEffect(() => {
    if (modal !== "requests") return;
    const controller = new AbortController();
    lenderGet(`${endpoint}/deposits?page=${page}`, controller.signal)
      .then(setDeposits)
      .catch((failure) => {
        if (!controller.signal.aborted) setError(failure.message);
      });
    return () => controller.abort();
  }, [endpoint, modal, page, version]);

  function open(type) {
    setError("");
    setNotice("");
    setSelected(null);
    setPage(0);
    setDeposits(null);
    if (type === "add") {
      setAmount("");
      setKey(crypto.randomUUID());
    }
    setModal(type);
  }

  async function submit(event) {
    event.preventDefault();
    if (sending.current) return;
    sending.current = true;
    setBusy(true);
    setError("");
    try {
      if (modal === "add") {
        await lenderPost(`${endpoint}/deposits`, { amount, clientRequestId: key });
        setModal(null);
        setNotice("Deposit requested. Cash becomes available after approval.");
      } else {
        await lenderPost(`${endpoint}/deposits/${selected.id}/approve`, {});
        setSelected(null);
        setNotice(`Deposit ${selected.id} approved.`);
        onApproved();
      }
      setVersion((previous) => previous + 1);
    } catch (failure) {
      setError(failure.message);
    } finally {
      sending.current = false;
      setBusy(false);
    }
  }

  return (
    <section className="lender-funding" aria-label="Lender account funding">
      <div>
        <h2>Lender funding account</h2>
        <p>
          Available cash <strong>{balance ? money.format(balance.available) : "—"}</strong>
          {" · "}
          Reserved for buy-ins <strong>{balance ? money.format(balance.reserved) : "—"}</strong>
        </p>
        {balanceError && <p role="alert">{balanceError}</p>}
        {notice && !modal && <p role="status">{notice}</p>}
      </div>
      <div className="lender-funding-actions">
        {admin && <button className="ref-clear" onClick={() => open("requests")}>Deposit requests</button>}
        <button className="ref-primary" onClick={() => open("add")}>Add funds</button>
      </div>
      {modal && (
        <div className="admin-inventory-backdrop">
          <form className="admin-inventory-dialog" role="dialog" aria-modal="true"
            aria-labelledby="lender-funding-title" onSubmit={submit}>
            <header>
              <h2 id="lender-funding-title">{modal === "add" ? "Add funds" : "Deposit requests"}</h2>
              <button type="button" disabled={busy} onClick={() => setModal(null)}>Close</button>
            </header>
            {modal === "add" ? (
              <>
                <p>Submit a deposit request for your workspace administrator to approve.</p>
                <label>
                  Amount (INR)
                  <input type="number" min="0.01" step="0.01" value={amount}
                    onChange={(event) => setAmount(event.target.value)} required />
                </label>
                <footer><button className="primary" disabled={busy}>Submit request</button></footer>
              </>
            ) : selected ? (
              <>
                <p>Confirm receipt of {money.format(selected.amount)} for deposit {selected.id}.</p>
                <footer>
                  <button type="button" disabled={busy} onClick={() => setSelected(null)}>Back</button>
                  <button className="primary" disabled={busy}>Accept deposit</button>
                </footer>
              </>
            ) : (
              <>
                {!deposits && !error && <p role="status">Loading deposit requests…</p>}
                {deposits?.content?.length === 0 && <p>No deposit requests.</p>}
                <ul className="lender-deposit-list">
                  {deposits?.content?.map((deposit) => (
                    <li key={deposit.id}>
                      <span>#{deposit.id} · {money.format(deposit.amount)} · {deposit.status === "PENDING" ? "Pending" : "Confirmed"}</span>
                      {deposit.status === "PENDING" && <button type="button" onClick={() => setSelected(deposit)}>Review</button>}
                    </li>
                  ))}
                </ul>
                {deposits?.totalPages > 1 && (
                  <footer>
                    <button type="button" disabled={page === 0}
                      onClick={() => { setDeposits(null); setPage(page - 1); }}>Previous</button>
                    <button type="button" disabled={page >= deposits.totalPages - 1}
                      onClick={() => { setDeposits(null); setPage(page + 1); }}>Next</button>
                  </footer>
                )}
              </>
            )}
            {notice && <p role="status">{notice}</p>}
            {error && <p className="admin-inventory-error" role="alert">{error}</p>}
          </form>
        </div>
      )}
    </section>
  );
}
