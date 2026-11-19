import PaginatedTable from '../components/PaginatedTable'
import { Fragment, useEffect, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { lenderGet } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import { useReferenceSort } from '../hooks/useReferenceSort'
import { LoanBadge, LoanCard } from '../components/loans/LoanCards'
import RunAccrualButton from '../components/loans/RunAccrualButton'
import { num, loanId, display, downloadLoans } from '../utils/loanFormat'
import './ReferencePages.css'
import './LoansPage.css'
import './LiveValuationsPage.css'
import './LiveFeesRebatesPage.css'
const money = (v, currency = 'INR') =>
  v == null
    ? 'Not recorded'
    : new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 6,
      }).format(v)
export default function LiveFeesRebatesPage() {
  const { organizationId, refreshKey, refresh } = useDataSource(),
    { searchTerm = '' } = useOutletContext(),
    [tab, setTab] = useState('ALL'),
    [loan, setLoan] = useState(''),
    [expanded, setExpanded] = useState(null),
    [state, setState] = useState({ scope: '', data: null, error: '' })
  const queryText = new URLSearchParams(
      Object.entries({ tab, loanId: loan, q: searchTerm }).filter(([, v]) => v),
    ).toString(),
    scope = `${organizationId}:${refreshKey}:${queryText}`
  useEffect(() => {
    const a = new AbortController()
    let inFlight = false
    async function load() {
      if (inFlight) return
      inFlight = true
      try {
        let page = 0,
          data,
          rows = []
        do {
          data = await lenderGet(
            `/api/v1/organizations/${organizationId}/lenddesk/fees-rebates/overview?${queryText}&page=${page++}&size=200`,
            a.signal,
          )
          rows.push(...data.records.content)
        } while (page < data.records.totalPages)
        if (!a.signal.aborted)
          setState({ scope, data: { ...data, rows }, error: '' })
      } catch (e) {
        if (!a.signal.aborted) setState(previous => ({ scope, data: previous.scope === scope ? previous.data : null, error: e.message }))
      } finally {
        inFlight = false
      }
    }
    load()
    const timer = setInterval(() => { if (!document.hidden) load() }, 10000)
    const focus = () => load()
    window.addEventListener('focus', focus)
    return () => { a.abort(); clearInterval(timer); window.removeEventListener('focus', focus) }
  }, [organizationId, refreshKey, queryText, scope])
  const data = state.scope === scope ? state.data : null,
    options =
      data ??
      (state.scope.startsWith(`${organizationId}:`) ? state.data : null),
    { sorted, sortHeading } = useReferenceSort(data?.rows ?? [])
  return (
    <section className="active-loans ref-page live-fees-rebates">
      <header className="al-heading">
        <div>
          <h1>
            Fees &amp; Rebates 
          </h1>
          <p>Recorded loan fees, cash rebates and outstanding settlements.</p>
        </div>
        <div>
          {data &&
            ['FEE', 'REBATE'].map((kind) => {
              const eligible = data.eligibleLoans.filter((l) => l.kind === kind)
              return (
                <RunAccrualButton
                  key={`${organizationId}:${loan}:${searchTerm}:${kind}`}
                  kind={kind}
                  loans={eligible}
                  onFinished={refresh}
                />
              )
            })}
        </div>
      </header>
      {data && (
        <>
          <div className="al-metrics">
            {data.totals.map((t) => (
              <Fragment key={t.currency}>
                <span>
                  {t.currency} fee accrued today{' '}
                  <b>{money(t.feeToday, t.currency)}</b>
                </span>
                <span>
                  Rebate accrued today <b>{money(t.rebateToday, t.currency)}</b>
                </span>
                <span>
                  Net recorded accrual{' '}
                  <b>{money(t.feeTotal - t.rebateTotal, t.currency)}</b>
                </span>
              </Fragment>
            ))}
            <span>
              Outstanding settlements <b>{data.counts.DUE}</b>
            </span>
          </div>
          <p className="fee-business-date">
            Business date: {data.businessDate} · {data.businessZone}. Accrual
            totals are recorded daily entries, not unpaid balances.
          </p>
        </>
      )}
      <nav className="al-tabs" aria-label="Fee and rebate views">
        {[
          ['ALL', 'All accruals'],
          ['FEE', 'Fees'],
          ['REBATE', 'Rebates'],
          ['DUE', 'Due settlement'],
        ].map(([value, label]) => (
          <button
            type="button"
            key={value}
            className={tab === value ? 'active' : ''}
            onClick={() => {
              setTab(value)
              setExpanded(null)
            }}
          >
            {label}
            {data && <b> {data.counts[value]}</b>}
          </button>
        ))}
      </nav>
      <div className="al-filters">
        <label>
          Loan
          <select
            value={loan}
            onChange={(e) => {
              setLoan(e.target.value)
              setExpanded(null)
            }}
          >
            <option value="">All loans</option>
            {options?.loans.map((l) => (
              <option key={l.id} value={l.id}>
                {l.label}
              </option>
            ))}
          </select>
        </label>
        <button onClick={refresh}>Refresh accruals</button>
        <button
          disabled={!data?.rows.length}
          onClick={() =>
            downloadLoans(sorted, [
              'id',
              'loanId',
              'borrower',
              'type',
              'entryDate',
              'currency',
              'rateBps',
              'amount',
              'accruedTotal',
              'settlementRule',
              'dueDate',
              'status',
            ])
          }
        >
          Export
        </button>
      </div>
      {state.scope !== scope ? (
        <p role="status">Loading fees and rebates…</p>
      ) : state.error ? (
        <p role="alert" className="al-error">
          Fees &amp; Rebates unavailable: {state.error}
        </p>
      ) : (
        <>
          <PaginatedTable className="al-table-scroll" label="Fees and rebates">
            <table className="al-table">
              <thead>
                <tr>
                  {[
                    ['id', tab === 'DUE' ? 'Settlement ID' : 'Accrual ID'],
                    ['loanId', 'Loan ID'],
                    ['borrower', 'Borrower'],
                    ['type', 'Type'],
                    ['entryDate', 'Accrual / period date'],
                    ['rateBps', 'Rate (bps p.a.)'],
                    [
                      'amount',
                      tab === 'DUE' ? 'Settlement amount' : 'Daily amount',
                    ],
                    ['accruedTotal', 'Recorded accrued total'],
                    ['settlementRule', 'Settlement rule'],
                    ['dueDate', 'Scheduled / due date'],
                    ['status', 'Status'],
                  ].map(([key, label]) => sortHeading(key, label))}
                </tr>
              </thead>
              <tbody>
                {sorted.map((r) => (
                  <Fragment key={r.key}>
                    <tr>
                      <td>
                        <button
                          aria-expanded={expanded === r.key}
                          onClick={() =>
                            setExpanded(expanded === r.key ? null : r.key)
                          }
                        >
                          {r.category === 'accruals'
                            ? 'ACC'
                            : r.type === 'FEE'
                              ? 'FEE'
                              : 'REB'}
                          -{String(r.id).padStart(6, '0')}
                        </button>
                      </td>
                      <td>
                        <Link to={`/loans/${loanId(r.loanId)}`}>
                          {loanId(r.loanId)}
                        </Link>
                      </td>
                      <td>{r.borrower}</td>
                      <td>
                        {r.type === 'FEE' ? 'Lending fee' : 'Cash rebate'}
                      </td>
                      <td>{r.entryDate}</td>
                      <td>{num(r.rateBps)}</td>
                      <td>{money(r.amount, r.currency)}</td>
                      <td>{money(r.accruedTotal, r.currency)}</td>
                      <td>{display(r.settlementRule)}</td>
                      <td>
                        {r.dueDate ??
                          (r.settlementRule === 'AT_CLOSE'
                            ? 'At loan close'
                            : 'Not scheduled')}
                      </td>
                      <td>
                        <LoanBadge>{display(r.status)}</LoanBadge>
                      </td>
                    </tr>
                    {expanded === r.key && (
                      <tr>
                        <td colSpan={11}>
                          <div className="al-detail-grid fee-detail-grid">
                            <LoanCard
                              title="Accrual context"
                              rows={[
                                ['Loan', loanId(r.loanId)],
                                ['Borrower', r.borrower],
                                ['Agreement', r.agreementNumber],
                                ['Security', r.symbol],
                                [
                                  'Type',
                                  r.type === 'FEE'
                                    ? 'Lending fee'
                                    : 'Cash rebate',
                                ],
                                ['Accrual date', r.entryDate],
                                [
                                  'Historical base value',
                                  money(r.baseValue, r.currency),
                                ],
                                ['Annual rate', `${num(r.rateBps)} bps`],
                                [
                                  'Recorded amount',
                                  money(r.amount, r.currency),
                                ],
                              ]}
                            />
                            <LoanCard
                              title="Settlement context"
                              rows={[
                                [
                                  'Current MSLA day basis',
                                  `Actual/${r.dayBasis}`,
                                ],
                                [
                                  'Current MSLA settlement rule',
                                  display(r.settlementRule),
                                ],
                                [
                                  'Scheduled / due date',
                                  r.dueDate ?? 'At loan close',
                                ],
                                [
                                  'Recorded settlement',
                                  display(r.settlementStatus),
                                ],
                                [
                                  'Settlement ID',
                                  r.settlementId ??
                                    (r.category !== 'accruals'
                                      ? r.id
                                      : 'Not issued'),
                                ],
                              ]}
                            />
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
                {!sorted.length && (
                  <tr>
                    <td colSpan={11}>
                      {tab === 'DUE'
                        ? 'No outstanding fee bills or rebate settlements match this view.'
                        : 'No recorded accruals match this view. Use the due accrual buttons to process eligible settled loans.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </PaginatedTable>

        </>
      )}
    </section>
  )
}
