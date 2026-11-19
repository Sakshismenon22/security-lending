import { useEffect, useRef, useState } from "react";
import { lenderGet, lenderPost } from "../api/lendDeskApi";
import "./BorrowerDirectoryPicker.css";

export default function BorrowerDirectoryPicker({
  organizationId,
  onConnected,
  onClose,
}) {
  const [state, setState] = useState({
    scope: "",
    rows: [],
    totalRegistered: 0,
    error: "",
  });
  const [revision, setRevision] = useState(0),
    [search, setSearch] = useState(""),
    [selectedId, setSelectedId] = useState(null);
  const [saving, setSaving] = useState(false),
    [error, setError] = useState(""),
    [page, setPage] = useState(0);
  const sending = useRef(false),
    panel = useRef(null);
  const scope = `${organizationId}:${revision}`;
  useEffect(() => {
    const controller = new AbortController();
    lenderGet(
      `/api/v1/organizations/${organizationId}/borrower-directory`,
      controller.signal,
    )
      .then((data) => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(data.content))
          throw new Error("The borrower directory response could not be read.");
        setState({
          scope,
          rows: data.content,
          totalRegistered: data.totalRegistered,
          error: "",
        });
        setSelectedId((current) =>
          data.content.some((row) => row.directoryId === current)
            ? current
            : null,
        );
      })
      .catch((e) => {
        if (!controller.signal.aborted)
          setState({ scope, rows: [], totalRegistered: 0, error: e.message });
      });
    return () => controller.abort();
  }, [organizationId, scope]);
  useEffect(() => {
    if (!onClose) return;
    const previous = document.activeElement,
      element = panel.current;
    element.querySelector("input, button")?.focus();
    function keydown(event) {
      if (event.key === "Escape") {
        event.preventDefault();
        if (!sending.current) onClose();
      }
      if (event.key !== "Tab") return;
      const items = [
        ...element.querySelectorAll(
          "button:not(:disabled), input:not(:disabled)",
        ),
      ];
      if (!items.length) {
        event.preventDefault();
        element.focus();
        return;
      }
      if (event.shiftKey && document.activeElement === items[0]) {
        event.preventDefault();
        items.at(-1).focus();
      } else if (!event.shiftKey && document.activeElement === items.at(-1)) {
        event.preventDefault();
        items[0].focus();
      }
    }
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    element.addEventListener("keydown", keydown);
    return () => {
      document.body.style.overflow = overflow;
      element.removeEventListener("keydown", keydown);
      previous?.focus();
    };
  }, [onClose]);
  const loading = state.scope !== scope;
  const filtered = state.rows.filter((row) =>
    `${row.name} ${row.registrationNumber} ${row.country}`
      .toLowerCase()
      .includes(search.trim().toLowerCase()),
  );
  const pages = Math.max(1, Math.ceil(filtered.length / 8)),
    currentPage = Math.min(page, pages - 1);
  const selected = state.rows.find((row) => row.directoryId === selectedId);
  async function connect(event) {
    event.preventDefault();
    if (!selected || sending.current || loading) return;
    sending.current = true;
    setSaving(true);
    setError("");
    try {
      const connection = await lenderPost(
        `/api/v1/organizations/${organizationId}/borrower-directory/connections`,
        { directoryId: selected.directoryId },
      );
      onConnected(connection);
    } catch (e) {
      setError(e.message);
    } finally {
      sending.current = false;
      setSaving(false);
    }
  }
  const content = (
    <section
      ref={panel}
      className="borrower-directory"
      role={onClose ? "dialog" : undefined}
      aria-modal={onClose ? true : undefined}
      aria-labelledby="borrower-directory-title"
      tabIndex={-1}
    >
      <header>
        <div>
          <span>REGISTERED COUNTERPARTIES</span>
          <h2 id="borrower-directory-title">Add registered borrower</h2>
        </div>
        {onClose && (
          <button
            type="button"
            className="bd-close"
            aria-label="Close borrower directory"
            disabled={saving}
            onClick={onClose}
          >
            ×
          </button>
        )}
      </header>
      <p>
        Select a borrower registered on Lendora. Account details are filled
        automatically, and borrowers already connected to your organization are
        excluded.
      </p>
      <form onSubmit={connect}>
        <div className="bd-toolbar">
          <label>
            <span>Search registered borrowers</span>
            <input
              type="search"
              value={search}
              onChange={(event) => {
                setSearch(event.target.value);
                setPage(0);
              }}
              disabled={saving}
              placeholder="Search by name, registration or country…"
            />
          </label>
          <button
            type="button"
            className="ref-secondary"
            disabled={saving || loading}
            onClick={() => {
              setError("");
              setRevision((value) => value + 1);
            }}
          >
            Refresh
          </button>
        </div>
        {loading ? (
          <p className="bd-empty" role="status">
            Loading registered borrowers…
          </p>
        ) : state.error ? (
          <div className="bd-error" role="alert">
            {state.error}
            <button
              type="button"
              onClick={() => setRevision((value) => value + 1)}
            >
              Retry
            </button>
          </div>
        ) : (
          <>
            <div className="bd-table-scroll">
              <table>
                <thead>
                  <tr>
                    <th aria-label="Select borrower" />
                    <th>Borrower organization</th>
                    <th>Registration number</th>
                    <th>Country</th>
                    <th>Currency</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered
                    .slice(currentPage * 8, (currentPage + 1) * 8)
                    .map((row) => (
                      <tr
                        key={row.directoryId}
                        className={
                          selectedId === row.directoryId ? "selected" : ""
                        }
                      >
                        <td>
                          <input
                            type="radio"
                            id={`bd-${row.directoryId}`}
                            name="registered-borrower"
                            aria-label={`Select ${row.name}`}
                            checked={selectedId === row.directoryId}
                            disabled={saving}
                            onChange={() => {
                              setSelectedId(row.directoryId);
                              setError("");
                            }}
                          />
                        </td>
                        <td>
                          <label htmlFor={`bd-${row.directoryId}`}>
                            {row.name}
                          </label>
                          <small>
                            Borrower account #{row.borrowerOrganizationId}
                          </small>
                        </td>
                        <td>{row.registrationNumber}</td>
                        <td>{row.country}</td>
                        <td>{row.currency}</td>
                      </tr>
                    ))}
                  {!filtered.length && (
                    <tr>
                      <td colSpan="5" className="bd-empty">
                        {state.rows.length
                          ? "No borrowers match your search."
                          : state.totalRegistered
                            ? "All registered borrowers are already connected."
                            : "No registered borrowers are available yet. Borrowers need to complete Lendora signup first."}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <div className="bd-pagination">
              <span>
                {filtered.length} available{" "}
                {filtered.length === 1 ? "borrower" : "borrowers"}
              </span>
              <div>
                <button
                  type="button"
                  disabled={saving || currentPage === 0}
                  onClick={() => setPage(currentPage - 1)}
                >
                  Previous
                </button>
                <span>
                  {currentPage + 1} / {pages}
                </span>
                <button
                  type="button"
                  disabled={saving || currentPage + 1 >= pages}
                  onClick={() => setPage(currentPage + 1)}
                >
                  Next
                </button>
              </div>
            </div>
          </>
        )}
        {selected && !loading && !state.error && (
          <div className="bd-selected">
            <span>Selected borrower</span>
            <strong>{selected.name}</strong>
            <small>
              {selected.registrationNumber} · {selected.country} ·{" "}
              {selected.currency}
            </small>
          </div>
        )}
        <p className="bd-note">
          This creates a counterparty connection. MSLA approval, credit limits
          and settlement remain separate steps.
        </p>
        {error && (
          <p className="bd-error" role="alert">
            {error}
          </p>
        )}
        <footer>
          {onClose && (
            <button type="button" disabled={saving} onClick={onClose}>
              Cancel
            </button>
          )}
          <button
            type="submit"
            className="ref-primary"
            disabled={!selected || saving || loading || Boolean(state.error)}
          >
            {saving ? "Connecting…" : "Add borrower"}
          </button>
        </footer>
      </form>
    </section>
  );
  return onClose ? (
    <div
      className="bd-backdrop"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget && !saving) onClose();
      }}
    >
      {content}
    </div>
  ) : (
    content
  );
}
