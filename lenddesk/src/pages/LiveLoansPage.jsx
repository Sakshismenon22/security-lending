import { useEffect, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { lenderGet } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import { useReferenceSort } from '../hooks/useReferenceSort'
import { LoanBadge } from '../components/loans/LoanCards'
import {
  num,
  money,
  downloadLoans,
  loanId,
  display,
  date,
} from '../utils/loanFormat'
const empty = { connectionId: '', mslaId: '', coverage: '', q: '' }
export default function LiveLoansPage() {
  const { searchTerm = '' } = useOutletContext(),
    { organizationId, refreshKey } = useDataSource()
  const [draft, setDraft] = useState(empty),
    [filters, setFilters] = useState(empty),
    [tab, setTab] = useState('ACTIVE'),
    [newLoanInfo, setNewLoanInfo] = useState(false),
    [state, setState] = useState({ scope: '', data: null, error: '' })
  const query = new URLSearchParams(
      Object.entries({
        ...filters,
        status: tab,
        q: [searchTerm, filters.q].filter(Boolean).join(' '),
      }).filter(([, v]) => v !== ''),
    ),
    queryText = query.toString(),
    scope = `${organizationId}:${refreshKey}:${queryText}`
  useEffect(() => {
    const abort = new AbortController()
    async function load() {
      try {
        let page = 0,
          result,
          rows = []
        do {
          result = await lenderGet(
            `/api/v1/organizations/${organizationId}/lenddesk/loans/overview?${queryText}&page=${page++}&size=200`,
            abort.signal,
          )
          rows.push(...result.loans.content)
        } while (page < result.loans.totalPages)
        if (!abort.signal.aborted)
          setState({
            scope,
            data: {
              ...result,
              rows: [...new Map(rows.map((l) => [l.id, l])).values()],
            },
            error: '',
          })
      } catch (error) {
        if (!abort.signal.aborted)
          setState({ scope, data: null, error: error.message })
      }
    }
    const timer = setTimeout(load, 150)
    return () => {
      clearTimeout(timer)
      abort.abort()
    }
  }, [organizationId, refreshKey, scope, queryText])
  const data = state.scope === scope ? state.data : null,
    { sorted, sortHeading } = useReferenceSort(data?.rows ?? [])
  const opts =
    data ?? (state.scope.startsWith(`${organizationId}:`) ? state.data : null)
  return (
    <section className="active-loans ref-page">
      <header className="al-heading">
        <div>
          <h1>
            Active Loans 
          </h1>
          <p>
            Monitor on-loan positions, collateral coverage and return
            obligations
          </p>
        </div>
        <div>
          <button
            disabled={!data}
            onClick={() =>
              downloadLoans(sorted, [
                'id',
                'borrower',
                'symbol',
                'currency',
                'outstandingQuantity',
                'marketValue',
                'feeBps',
                'eligibleCollateral',
                'coverage',
                'status',
              ])
            }
          >
            Export
          </button>
          <button
            className="ref-primary"
            onClick={() => setNewLoanInfo((v) => !v)}
          >
            New loan
          </button>
        </div>
      </header>
      {newLoanInfo && (
        <p role="status">
          New loans are requested by borrowers against an approved reservation.
          Open Pending review to approve or reject incoming loan requests, then
          Pending settlement to confirm settlement.
        </p>
      )}
      {data && (
        <div className="al-metrics">
          <span>
            Active loans <b>{data.activeLoanCount}</b>
          </span>
          {data.totals.map((t) => (
            <span key={t.currency}>
              {t.currency} on-loan / collateral{' '}
              <b>
                {money(t.marketValue, t.currency)} /{' '}
                {money(t.eligibleCollateral, t.currency)}
              </b>
              {t.missingValuations > 0 && (
                <small>{t.missingValuations} unvalued</small>
              )}
            </span>
          ))}
          <span>
            Open margin calls <b>{data.openMarginCallCount}</b>
          </span>
          <span>
            Open recalls <b>{data.openRecallCount}</b>
          </span>
        </div>
      )}
      <nav className="al-tabs">
        {[
          ['', 'All loans'],
          ['ACTIVE', 'Active'],
          ['APPROVED_PENDING_SETTLEMENT', 'Pending settlement'],
          ['PENDING_REVIEW', 'Pending review'],
          ['CLOSED', 'Closed'],
        ].map(([value, text]) => (
          <button
            key={text}
            className={tab === value ? 'active' : ''}
            onClick={() => setTab(value)}
          >
            {text}
          </button>
        ))}
      </nav>
      <form
        className="al-filters"
        onSubmit={(e) => {
          e.preventDefault()
          setFilters(draft)
        }}
      >
        <label>
          Borrower
          <select
            value={draft.connectionId}
            onChange={(e) =>
              setDraft({ ...draft, connectionId: e.target.value })
            }
          >
            <option value="">All borrowers</option>
            {opts?.borrowers.map((b) => (
              <option key={b.id} value={b.id}>
                {b.label}
              </option>
            ))}
          </select>
        </label>
        <input
          aria-label="Search live loan security"
          value={draft.q}
          placeholder="Security, borrower, MSLA or loan ID"
          onChange={(e) => setDraft({ ...draft, q: e.target.value })}
        />
        <label>
          MSLA
          <select
            value={draft.mslaId}
            onChange={(e) => setDraft({ ...draft, mslaId: e.target.value })}
          >
            <option value="">All MSLAs</option>
            {opts?.agreements.map((a) => (
              <option key={a.id} value={a.id}>
                {a.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          Coverage
          <select
            value={draft.coverage}
            onChange={(e) => setDraft({ ...draft, coverage: e.target.value })}
          >
            <option value="">All coverage</option>
            <option value="SUFFICIENT">Sufficient</option>
            <option value="SHORTFALL">Shortfall</option>
            <option value="UNKNOWN">Not valued</option>
          </select>
        </label>
        <button
          type="button"
          onClick={() => {
            setDraft(empty)
            setFilters(empty)
          }}
        >
          Reset
        </button>
        <button className="ref-primary">Apply</button>
      </form>
      {state.scope !== scope ? (
        <p role="status">Loading live loans…</p>
      ) : state.error ? (
        <p role="alert" className="al-error">
          Loans unavailable: {state.error}
        </p>
      ) : (
        <>
          <div className="al-table-scroll">
            <table className="al-table">
              <thead>
                <tr>
                  {[
                    ['id', 'Loan ID'],
                    ['borrower', 'Borrower'],
                    ['symbol', 'Security'],
                    ['outstandingQuantity', 'Outstanding qty'],
                    ['marketValue', 'Market value'],
                    ['feeBps', 'Fee (bps p.a.)'],
                    ['eligibleCollateral', 'Eligible collateral'],
                    ['coverage', 'Coverage'],
                    ['maturity', 'Maturity'],
                    ['openMarginCount', 'Margin'],
                    ['openRecallCount', 'Recall'],
                    ['status', 'Status'],
                  ].map(([key, text]) => sortHeading(key, text))}
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {sorted.map((l) => (
                  <tr key={l.id}>
                    <td><Link to={`/loans/${loanId(l.id)}/manage`}>{loanId(l.id)}</Link></td>
                    <td>{l.borrower}</td>
                    <td>{l.symbol}</td>
                    <td>{num(l.outstandingQuantity)}</td>
                    <td>{money(l.marketValue, l.currency)}</td>
                    <td>{num(l.feeBps)}</td>
                    <td>{money(l.eligibleCollateral, l.currency)}</td>
                    <td>
                      {l.coverage == null
                        ? 'Not valued'
                        : `${num(l.coverage)}%`}
                    </td>
                    <td>{date(l.maturityAt ?? l.maturity)}</td>
                    <td>
                      {l.openMarginCount ? (
                        <LoanBadge>Margin open</LoanBadge>
                      ) : l.pendingMarginRequest ? (
                        'Request pending'
                      ) : l.shortfall > 0 ? (
                        'Shortfall'
                      ) : l.status !== 'ACTIVE' ? (
                        'Not applicable'
                      ) : l.shortfall == null ? (
                        'Not valued'
                      ) : (
                        'Clear'
                      )}
                    </td>
                    <td>
                      {l.openRecallCount ? (
                        <LoanBadge>Recall open</LoanBadge>
                      ) : (
                        'None'
                      )}
                    </td>
                    <td>
                      <LoanBadge>{display(l.status)}</LoanBadge>
                    </td>
                    <td>
                      <Link to={`/loans/${loanId(l.id)}/manage`}>Manage</Link>
                    </td>
                  </tr>
                ))}
                {!sorted.length && (
                  <tr>
                    <td colSpan={13}>No matching loans.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  )
}
