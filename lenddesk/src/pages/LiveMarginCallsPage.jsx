import { Fragment, useCallback, useEffect, useMemo, useState } from 'react'
import { Link, NavLink, useOutletContext } from 'react-router-dom'
import { lenderGet, lenderPost, lenderPut } from '../api/lendDeskApi'
import ReferenceTable, { Badge } from '../components/reference/ReferenceTable'
import { useDataSource } from '../hooks/useDataSource'
import './ReferencePages.css'
import './LifecycleExceptionsPage.css'

const money = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(Number(value ?? 0))
const label = (value) => String(value ?? '—').toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
const callId = (id) => `MC-${String(id).padStart(6, '0')}`
const loanId = (id) => `LOAN-${String(id).padStart(6, '0')}`
const stamp = (value) => value ? new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)).replace(',', '') : '—'
const istInput = (value) => new Date(new Date(value).getTime() + 330 * 60000).toISOString().slice(0, 16)
const open = new Set(['PENDING', 'OPEN', 'PARTIALLY_FULFILLED', 'OVERDUE', 'CANCELLATION_PENDING'])
const tone = (value) => /overdue|rejected|failed/i.test(value) ? 'risk' : /pending|open|partially/i.test(value) ? 'warn' : 'good'
const State = ({ value }) => <Badge tone={tone(value)}>{label(value)}</Badge>

function IssueMarginCall({ organizationId, onClose, onDone }) {
  const [loans, setLoans] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [loan, setLoan] = useState('')
  const [amount, setAmount] = useState('')
  const [dueAt, setDueAt] = useState(() => istInput(Date.now() + 24 * 60 * 60 * 1000))
  const [minimumDueAt] = useState(() => istInput(Date.now() + 60000))
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [guardEnabled, setGuardEnabled] = useState(true)
  const load = useCallback(async (signal) => {
    setLoading(true)
    try {
      const [guard, data] = await Promise.all([
        lenderGet(`/api/v1/organizations/${organizationId}/settings/margin-shortfall-guardrail`, signal),
        lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/loans/overview?status=ACTIVE&page=0&size=200`, signal),
      ])
      if (!signal.aborted) {
        setGuardEnabled(guard.enabled !== false)
        setLoans((data.loans?.content ?? []).filter((item) => item.canIssueMargin))
      }
    } catch (e) { if (!signal.aborted) setError(e.message) } finally { if (!signal.aborted) setLoading(false) }
  }, [organizationId])
  useEffect(() => {
    const abort = new AbortController()
    queueMicrotask(() => { void load(abort.signal) })
    return () => abort.abort()
  }, [load])
  const selected = loans.find((item) => String(item.id) === loan)
  async function submit() {
    setBusy(true); setError('')
    try {
      await lenderPost(`/api/v1/organizations/${organizationId}/loans/${loan}/margin-call-requests`, { amount: Number(amount), dueAt: new Date(`${dueAt}+05:30`).toISOString(), reason: reason.trim(), clientRequestId: crypto.randomUUID() })
      onDone(`Margin-call request for ${loanId(loan)} was queued for Lifecycle Service processing and borrower delivery.`)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  async function toggleGuard() {
    setBusy(true); setError('')
    try {
      const result = await lenderPut(`/api/v1/organizations/${organizationId}/settings/margin-shortfall-guardrail`, { enabled: !guardEnabled })
      setGuardEnabled(result.enabled !== false)
      await load(new AbortController().signal)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return <dialog className="live-margin-dialog" open onCancel={onClose}><form onSubmit={(event) => { event.preventDefault(); submit() }}><header><h2>Issue margin call</h2><button type="button" onClick={onClose} aria-label="Close">×</button></header>{loading ? <p>Loading eligible loans…</p> : <><div className="live-margin-shortfall"><b>Margin shortfall guardrail</b><button type="button" className={guardEnabled ? 'ref-primary' : 'ref-clear'} onClick={toggleGuard} disabled={busy}>{guardEnabled ? 'ON — shortfall required' : 'OFF — demo calls allowed'}</button><p>{guardEnabled ? 'Only under-collateralized loans can receive a margin call.' : 'Demo mode: any active loan can receive a positive margin call. Re-enable it after the demonstration.'}</p></div><label>Eligible loan<select required value={loan} onChange={(event) => { const value = event.target.value; setLoan(value); const found = loans.find((item) => String(item.id) === value); setAmount(found?.shortfall > 0 ? found.shortfall : '') }}><option value="">{guardEnabled ? 'Select an under-collateralized active loan' : 'Select an active loan for the demo'}</option>{loans.map((item) => <option key={item.id} value={item.id}>{loanId(item.id)} · {item.borrower} · {item.symbol} · shortfall {money(item.shortfall)}</option>)}</select></label>{selected && <p className="live-margin-shortfall">Current collateral shortfall: <b>{money(selected.shortfall)}</b>. {guardEnabled ? 'The requested amount must cover this amount.' : 'The amount may be used for the demo even without a shortfall.'}</p>}<label>Requested amount (INR)<input required type="number" min={Math.max(.01, guardEnabled ? Number(selected?.shortfall ?? .01) : .01)} step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} /></label><label>Response deadline (IST)<input required type="datetime-local" min={minimumDueAt} value={dueAt} onChange={(event) => setDueAt(event.target.value)} /></label><label>Reason / borrower message<textarea required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>{error && <p className="al-error" role="alert">{error}</p>}<footer><button type="button" onClick={onClose}>Cancel</button><button className="ref-primary" disabled={busy || !loan || !reason.trim()}>{busy ? 'Submitting…' : 'Issue live margin call'}</button></footer></>}</form></dialog>
}

export default function LiveMarginCallsPage() {
  const { searchTerm = '' } = useOutletContext()
  const { organizationId, refreshKey, refresh } = useDataSource()
  const [state, setState] = useState({ scope: '', data: [], loading: true, error: '' })
  const [status, setStatus] = useState('All statuses')
  const [expanded, setExpanded] = useState(null)
  const [issueOpen, setIssueOpen] = useState(false)
  const [cancelTarget, setCancelTarget] = useState(null)
  const [cancelBusy, setCancelBusy] = useState(false)
  const [cancelError, setCancelError] = useState('')
  async function cancelCall() {
    setCancelBusy(true); setCancelError('')
    try {
      await lenderPost(`/api/v1/organizations/${organizationId}/margin-calls/${cancelTarget.id}/cancel`, { reference: `cancel-margin-${crypto.randomUUID()}` })
      setCancelTarget(null)
      setNotice('Cancellation requested. BorrowDesk will verify that no collateral response has been submitted before the call is cancelled.')
      refresh()
    } catch (e) { setCancelError(e.message) } finally { setCancelBusy(false) }
  }
  useEffect(() => { const timer = setInterval(() => { if (!document.hidden) refresh() }, 10000); return () => clearInterval(timer) }, [refresh])
  const [notice, setNotice] = useState('')
  const [asOf] = useState(() => Date.now())
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => { const abort = new AbortController(); lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/margin-calls/overview?page=0&size=200`, abort.signal).then((data) => { if (!abort.signal.aborted) setState({ scope, data: data.content ?? [], loading: false, error: '' }) }).catch((error) => { if (!abort.signal.aborted) setState({ scope, data: [], loading: false, error: error.message }) }); return () => abort.abort() }, [organizationId, refreshKey, scope])
  const rows = useMemo(() => state.scope === scope ? state.data : [], [scope, state.data, state.scope])
  const filtered = useMemo(() => rows.filter((row) => (status === 'All statuses' || row.status === status) && Object.values(row).join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [rows, searchTerm, status])
  const active = rows.filter((row) => open.has(row.status))
  const remaining = active.reduce((sum, row) => sum + Number(row.remainingAmount ?? 0), 0)
  function done(message) { setIssueOpen(false); setNotice(message); refresh() }
  return <section className="ref-page lifecycle-page lifecycle-margin-calls live-margin-calls"><header className="ref-heading"><h1>Lifecycle Exceptions </h1><nav className="lifecycle-tabs" aria-label="Lifecycle views"><NavLink to="/returns">Returns</NavLink><NavLink to="/recalls">Recalls</NavLink><NavLink to="/margin-calls">Margin Calls</NavLink></nav><button type="button" className="ref-primary lifecycle-workflow-link" onClick={() => setIssueOpen(true)}>Issue margin call</button></header>
    <section className="lifecycle-metrics" aria-label="Margin call summary"><div><span>⚠</span><div><small>Open calls</small><strong>{active.length}</strong></div></div><div><span>▱</span><div><small>Outstanding deficit</small><strong>{money(remaining)}</strong></div></div><div><span>◷</span><div><small>Due today / overdue</small><strong>{active.filter((row) => new Date(row.dueAt) <= new Date(asOf + 86400000)).length}</strong></div></div><div><span>✓</span><div><small>Fulfilled calls</small><strong>{rows.filter((row) => row.status === 'FULFILLED').length}</strong></div></div></section>
    <section className="live-return-toolbar"><label>Call status<select value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option>{[...new Set(rows.map((row) => row.status))].map((value) => <option key={value} value={value}>{label(value)}</option>)}</select></label><button className="ref-clear" type="button" onClick={() => setStatus('All statuses')}>Clear</button><button className="ref-primary" type="button" onClick={refresh}>Refresh live data</button></section>
    {notice && <p className="live-return-notice" role="status">{notice}</p>}{issueOpen && <IssueMarginCall organizationId={organizationId} onClose={() => setIssueOpen(false)} onDone={done} />}
    {cancelTarget && <dialog open className="live-margin-dialog" onCancel={() => { if (!cancelBusy) setCancelTarget(null) }}><form onSubmit={event => { event.preventDefault(); if (!cancelBusy) cancelCall() }}><header><h2>Cancel margin call</h2></header><p>Cancel {callId(cancelTarget.id)} for {loanId(cancelTarget.loanId)}? Cancellation is allowed only before the borrower submits collateral, including any response still in transit.</p>{cancelError && <p role="alert" className="al-error">{cancelError}</p>}<footer><button type="button" disabled={cancelBusy} onClick={() => setCancelTarget(null)}>Keep margin call</button><button className="ref-primary" disabled={cancelBusy}>{cancelBusy ? 'Requesting…' : 'Confirm cancellation'}</button></footer></form></dialog>}
    {state.loading || state.scope !== scope ? <p className="ref-empty" role="status">Loading live margin calls…</p> : state.error ? <p className="live-error" role="alert">Live margin calls unavailable: {state.error}</p> : <div className="ref-scroll"><table className="ref-main-table lifecycle-table live-margin-table"><thead><tr><th aria-label="Expand call"/><th>Call ID</th><th>Loan ID</th><th>Borrower</th><th>Security</th><th>Required amount</th><th>Fulfilled</th><th>Remaining</th><th>Required by IST</th><th>Status</th><th>Action</th></tr></thead><tbody>{filtered.map((row) => <Fragment key={row.id}><tr className={expanded === row.id ? 'ref-selected' : ''}><td><button className="ref-expander" type="button" aria-expanded={expanded === row.id} aria-label={`${expanded === row.id ? 'Collapse' : 'Expand'} ${callId(row.id)}`} onClick={() => setExpanded((current) => current === row.id ? null : row.id)}>{expanded === row.id ? '⌄' : '›'}</button></td><td><button className="ref-id" type="button" onClick={() => setExpanded((current) => current === row.id ? null : row.id)}>{callId(row.id)}</button></td><td><Link to={`/loans/${row.loanId}`}>{loanId(row.loanId)}</Link></td><td>{row.borrowerName ?? '—'}</td><td><b>{row.securitySymbol ?? '—'}</b>{row.securityName ? ` · ${row.securityName}` : ''}</td><td className="ref-num">{money(row.amount)}</td><td className="ref-num">{money(row.fulfilledAmount)}</td><td className="ref-num">{money(row.remainingAmount)}</td><td>{stamp(row.dueAt)}</td><td><State value={row.status} /></td><td><div className="live-margin-row-actions"><button className="live-margin-icon-button" type="button" aria-label={`${open.has(row.status) ? 'Track' : 'View'} ${callId(row.id)}`} title={`${open.has(row.status) ? 'Track' : 'View'} margin call`} onClick={() => setExpanded(row.id)}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/></svg></button>{row.canCancel && <button type="button" className="live-margin-icon-button live-margin-cancel-button" aria-label="Cancel margin call" title="Cancel margin call" onClick={() => { setCancelError(''); setCancelTarget(row) }}><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="m8.5 8.5 7 7m0-7-7 7"/></svg></button>}</div></td></tr>{expanded === row.id && <tr className="ref-child"><td colSpan="11"><div className="live-return-details"><ReferenceTable title={`⚠ Margin call summary — ${callId(row.id)}`} headers={['Field', 'Value']} rows={[["Loan", loanId(row.loanId)], ["Borrower", row.borrowerName ?? '—'], ["Security", `${row.securitySymbol ?? '—'}${row.securityName ? ` · ${row.securityName}` : ''}`], ["Required amount", money(row.amount)], ["Fulfilled collateral", money(row.fulfilledAmount)], ["Remaining deficit", money(row.remainingAmount)], ["Required by IST", stamp(row.dueAt)], ["Reason", row.reason], ["Status", <State value={row.status} />]]} /><div className="live-margin-actions">{row.cancellationFailureReason && <p role="alert">Cancellation declined: {row.cancellationFailureReason}</p>}{row.status === 'CANCELLATION_PENDING' && <p role="status">Waiting for BorrowDesk to verify that no collateral response has been submitted.</p>}<p>{open.has(row.status) ? 'The borrower must pledge eligible collateral. Confirm the received pledge from the Collateral screen; the lender service then updates this call to partially fulfilled or fulfilled.' : 'This margin call has no remaining lender action.'}</p><div><Link className="ref-clear" to={`/loans/${row.loanId}`}>Open loan</Link>{open.has(row.status) && <Link className="ref-primary" to="/collateral">Review collateral receipt</Link>}</div></div></div></td></tr>}</Fragment>)}{!filtered.length && <tr><td colSpan="11" className="ref-empty">No live margin calls match this view.</td></tr>}</tbody></table></div>}
  </section>
}
