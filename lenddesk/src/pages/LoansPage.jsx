import { useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { useDataSource } from '../hooks/useDataSource'
import { activeLoans } from '../data/activeLoanData'
import LiveLoansPage from './LiveLoansPage'
import { useReferenceSort } from '../hooks/useReferenceSort'
import './ReferencePages.css'
import './LoansPage.css'
import { LoanBadge } from '../components/loans/LoanCards'
import { num, money, downloadLoans } from '../utils/loanFormat'
export default function LoansPage() {
  const { mode } = useDataSource()
  return mode === 'live' ? <LiveLoansPage /> : <DemoLoans />
}
function DemoLoans() {
  const { searchTerm = '' } = useOutletContext(),
    [tab, setTab] = useState('Active'),
    [draft, setDraft] = useState({
      borrower: '',
      q: '',
      msla: '',
      coverage: '',
      maturity: '',
    }),
    [filters, setFilters] = useState(draft)
  const filtered = activeLoans.filter(
    (l) =>
      !['Closed', 'Pending settlement'].includes(tab) &&
      (!filters.borrower || l.borrower === filters.borrower) &&
      (!filters.msla || l.msla === filters.msla) &&
      (!filters.coverage || filters.coverage === 'Sufficient') &&
      (!filters.maturity || l.maturity === filters.maturity) &&
      `${l.symbol} ${l.id} ${l.borrower}`
        .toLowerCase()
        .includes(`${filters.q}`.toLowerCase()) &&
      Object.values(l)
        .join(' ')
        .toLowerCase()
        .includes(searchTerm.toLowerCase()),
  )
  const { sorted: sortedRows, sortHeading } = useReferenceSort(filtered)
  function exportRows() {
    const keys = [
      'id',
      'borrower',
      'symbol',
      'outstanding',
      'value',
      'fee',
      'collateral',
      'coverage',
      'maturity',
      'margin',
      'recall',
      'status',
    ]
    downloadLoans(sortedRows, keys)
  }
  return (
    <section className="active-loans ref-page">
      <header className="al-heading">
        <div>
          <h1>
            Active Loans <span className="ref-demo">DEMO DATA</span>
          </h1>
          <p>
            Monitor on-loan positions, collateral coverage and return
            obligations
          </p>
        </div>
        <div>
          <button onClick={exportRows}>♧ Export</button>
          <Link className="ref-primary" to="/reservations">
            New loan
          </Link>
        </div>
      </header>
      <div className="al-metrics">
        {[
          ['Active loans', '128'],
          ['On-loan value', '$84.60M'],
          ['Eligible collateral', '$89.44M'],
          ['Open margin calls', '4'],
          ['Open recalls', '6'],
        ].map(([k, v]) => (
          <span key={k}>
            {k}
            <b>{v}</b>
          </span>
        ))}
      </div>
      <nav className="al-tabs">
        {['All loans', 'Active', 'Pending settlement', 'Closed'].map((t) => (
          <button
            key={t}
            className={tab === t ? 'active' : ''}
            onClick={() => setTab(t)}
          >
            {t}
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
            value={draft.borrower}
            onChange={(e) => setDraft({ ...draft, borrower: e.target.value })}
          >
            <option value="">All borrowers</option>
            {[...new Set(activeLoans.map((l) => l.borrower))].map((v) => (
              <option key={v}>{v}</option>
            ))}
          </select>
        </label>
        <input
          aria-label="Search loan security"
          placeholder="Search security (e.g., NVDA, AAPL, ISIN, CUSIP)…"
          value={draft.q}
          onChange={(e) => setDraft({ ...draft, q: e.target.value })}
        />
        <label>
          MSLA
          <select
            value={draft.msla}
            onChange={(e) => setDraft({ ...draft, msla: e.target.value })}
          >
            <option value="">All MSLAs</option>
            <option>MSLA-00124</option>
          </select>
        </label>
        <label>
          Coverage
          <select
            value={draft.coverage}
            onChange={(e) => setDraft({ ...draft, coverage: e.target.value })}
          >
            <option value="">All coverage</option>
            <option>Sufficient</option>
            <option>Shortfall</option>
          </select>
        </label>
        <label>
          Maturity date
          <select
            value={draft.maturity}
            onChange={(e) => setDraft({ ...draft, maturity: e.target.value })}
          >
            <option value="">Any date</option>
            {activeLoans.map((l) => (
              <option key={l.id}>{l.maturity}</option>
            ))}
          </select>
        </label>
        <button
          type="button"
          onClick={() => {
            const f = {
              borrower: '',
              q: '',
              msla: '',
              coverage: '',
              maturity: '',
            }
            setDraft(f)
            setFilters(f)
          }}
        >
          Reset
        </button>
        <button className="ref-primary">Apply</button>
      </form>
      <div className="al-table-scroll">
        <table className="al-table">
          <thead>
            <tr>
              {[
                ['id', 'Loan ID'],
                ['borrower', 'Borrower'],
                ['symbol', 'Security'],
                ['outstanding', 'Outstanding qty'],
                ['value', 'Market value'],
                ['fee', 'Fee (bps p.a.)'],
                ['collateral', 'Eligible collateral'],
                ['coverage', 'Coverage'],
                ['maturity', 'Maturity'],
                ['margin', 'Margin'],
                ['recall', 'Recall'],
                ['status', 'Status'],
              ].map(([key, label]) => sortHeading(key, label))}
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {sortedRows.map((l) => (
              <tr key={l.id}>
                <td><Link to={`/loans/${l.id}/manage`}>{l.id}</Link></td>
                <td>{l.borrower}</td>
                <td>{l.symbol}</td>
                <td>{num(l.outstanding)}</td>
                <td>{money(l.value)}</td>
                <td>{l.fee}</td>
                <td>{money(l.collateral)}</td>
                <td>{l.coverage.toFixed(2)}%</td>
                <td>{l.maturity}</td>
                <td>
                  {l.margin === 'Clear' ? (
                    l.margin
                  ) : (
                    <LoanBadge>{l.margin}</LoanBadge>
                  )}
                </td>
                <td>
                  {l.recall === 'None' ? (
                    l.recall
                  ) : (
                    <LoanBadge>{l.recall}</LoanBadge>
                  )}
                </td>
                <td>
                  <LoanBadge>{l.status}</LoanBadge>
                </td>
                <td>
                  <Link to={`/loans/${l.id}/manage`}>Manage</Link>
                </td>
              </tr>
            ))}
            {!sortedRows.length && (
              <tr>
                <td colSpan={13}>No matching loans.</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </section>
  )
}
