import { Fragment, useEffect, useMemo, useState } from 'react'
import { Link, NavLink, useOutletContext } from 'react-router-dom'
import { lenderGet, lenderPost } from '../api/lendDeskApi'
import ReferenceTable, { Badge } from '../components/reference/ReferenceTable'
import { useDataSource } from '../hooks/useDataSource'
import './ReferencePages.css'
import './LifecycleExceptionsPage.css'

const number = (value) => Number(value ?? 0).toLocaleString('en-US')
const label = (value) => String(value ?? '—').toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
const recallId = (id) => `REC-${String(id).padStart(6, '0')}`
const loanId = (id) => `LOAN-${String(id).padStart(6, '0')}`
const stamp = (value) => value ? new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)).replace(',', '') : '—'
const open = new Set(['OPEN', 'PARTIALLY_FULFILLED', 'OVERDUE'])
const tone = (value) => /overdue|failed|rejected/i.test(value) ? 'risk' : /open|partially|pending/i.test(value) ? 'warn' : 'good'
const State = ({ value }) => <Badge tone={tone(value)}>{label(value)}</Badge>

function IssueRecall({ organizationId, onClose, onDone }) {
  const [loans, setLoans] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [loan, setLoan] = useState('')
  const [quantity, setQuantity] = useState('')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  useEffect(() => { const abort = new AbortController(); lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/loans/overview?status=ACTIVE&page=0&size=200`, abort.signal).then((data) => { if (!abort.signal.aborted) setLoans((data.loans?.content ?? []).filter((item) => item.canRecall)) }).catch((e) => { if (!abort.signal.aborted) setError(e.message) }).finally(() => { if (!abort.signal.aborted) setLoading(false) }); return () => abort.abort() }, [organizationId])
  const selected = loans.find((item) => String(item.id) === loan)
  async function submit() {
    setBusy(true); setError('')
    try {
      await lenderPost(`/api/v1/organizations/${organizationId}/loans/${loan}/recall-requests`, { quantity: Number(quantity), reason: reason.trim(), clientRequestId: crypto.randomUUID() })
      onDone(`Recall request for ${loanId(loan)} was queued for Lifecycle Service processing and borrower delivery.`)
    } catch (e) { setError(e.message) } finally { setBusy(false) }
  }
  return <dialog className="live-margin-dialog" open onCancel={onClose}><form onSubmit={(event) => { event.preventDefault(); submit() }}><header><h2>Create recall</h2><button type="button" onClick={onClose} aria-label="Close">×</button></header>{loading ? <p>Loading loans eligible for recall…</p> : <><label>Eligible loan<select required value={loan} onChange={(event) => { const value = event.target.value; setLoan(value); const found = loans.find((item) => String(item.id) === value); setQuantity(found?.recallAvailableQuantity ?? '') }}><option value="">Select an active loan with available quantity</option>{loans.map((item) => <option key={item.id} value={item.id}>{loanId(item.id)} · {item.borrower} · {item.symbol} · available {number(item.recallAvailableQuantity)} shares</option>)}</select></label>{selected && <p className="live-margin-shortfall">Available for recall: <b>{number(selected.recallAvailableQuantity)} shares</b>. The lifecycle service calculates the response deadline from the MSLA notice period.</p>}<label>Recall quantity<input required type="number" min="1" max={selected?.recallAvailableQuantity ?? 0} step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label><label>Reason / borrower message<textarea required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} /></label>{error && <p className="al-error" role="alert">{error}</p>}<footer><button type="button" onClick={onClose}>Cancel</button><button className="ref-primary" disabled={busy || !loan || !reason.trim()}>{busy ? 'Submitting…' : 'Create live recall'}</button></footer></>}</form></dialog>
}

export default function LiveRecallsPage() {
  const { searchTerm = '' } = useOutletContext()
  const { organizationId, refreshKey, refresh } = useDataSource()
  const [state, setState] = useState({ scope: '', data: [], loading: true, error: '' })
  const [status, setStatus] = useState('All statuses')
  const [expanded, setExpanded] = useState(null)
  const [issueOpen, setIssueOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const [asOf] = useState(() => Date.now())
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => { const abort = new AbortController(); lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/recalls/overview?page=0&size=200`, abort.signal).then((data) => { if (!abort.signal.aborted) setState({ scope, data: data.content ?? [], loading: false, error: '' }) }).catch((error) => { if (!abort.signal.aborted) setState({ scope, data: [], loading: false, error: error.message }) }); return () => abort.abort() }, [organizationId, refreshKey, scope])
  const rows = useMemo(() => state.scope === scope ? state.data : [], [scope, state.data, state.scope])
  const filtered = useMemo(() => rows.filter((row) => (status === 'All statuses' || row.status === status) && Object.values(row).join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [rows, searchTerm, status])
  const active = rows.filter((row) => open.has(row.status))
  const remaining = active.reduce((sum, row) => sum + Number(row.remainingQuantity ?? 0), 0)
  function done(message) { setIssueOpen(false); setNotice(message); refresh() }
  return <section className="ref-page lifecycle-page lifecycle-recalls live-recalls"><header className="ref-heading"><h1>Lifecycle Exceptions </h1><nav className="lifecycle-tabs" aria-label="Lifecycle views"><NavLink to="/returns">Returns</NavLink><NavLink to="/recalls">Recalls</NavLink><NavLink to="/margin-calls">Margin Calls</NavLink></nav><button type="button" className="ref-primary lifecycle-workflow-link" onClick={() => setIssueOpen(true)}>Create recall</button></header>
    <section className="lifecycle-metrics" aria-label="Recall summary"><div><span>⚑</span><div><small>Open recalls</small><strong>{active.length}</strong></div></div><div><span>⇄</span><div><small>Outstanding quantity</small><strong>{number(remaining)}</strong></div></div><div><span>◷</span><div><small>Due today / overdue</small><strong>{active.filter((row) => new Date(row.dueAt) <= new Date(asOf + 86400000)).length}</strong></div></div><div><span>✓</span><div><small>Fulfilled recalls</small><strong>{rows.filter((row) => row.status === 'FULFILLED').length}</strong></div></div></section>
    <section className="live-return-toolbar"><label>Recall status<select value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option>{[...new Set(rows.map((row) => row.status))].map((value) => <option key={value} value={value}>{label(value)}</option>)}</select></label><button className="ref-clear" type="button" onClick={() => setStatus('All statuses')}>Clear</button><button className="ref-primary" type="button" onClick={refresh}>Refresh live data</button></section>
    {notice && <p className="live-return-notice" role="status">{notice}</p>}{issueOpen && <IssueRecall organizationId={organizationId} onClose={() => setIssueOpen(false)} onDone={done} />}
    {state.loading || state.scope !== scope ? <p className="ref-empty" role="status">Loading live recalls…</p> : state.error ? <p className="live-error" role="alert">Live recalls unavailable: {state.error}</p> : <div className="ref-scroll"><table className="ref-main-table lifecycle-table live-recall-table"><thead><tr><th aria-label="Expand recall"/><th>Recall ID</th><th>Loan ID</th><th>Borrower</th><th>Security</th><th>Recall quantity</th><th>Fulfilled</th><th>Remaining</th><th>Pending returns</th><th>Return by IST</th><th>Status</th><th>Action</th></tr></thead><tbody>{filtered.map((row) => <Fragment key={row.id}><tr className={expanded === row.id ? 'ref-selected' : ''}><td><button className="ref-expander" type="button" aria-expanded={expanded === row.id} aria-label={`${expanded === row.id ? 'Collapse' : 'Expand'} ${recallId(row.id)}`} onClick={() => setExpanded((current) => current === row.id ? null : row.id)}>{expanded === row.id ? '⌄' : '›'}</button></td><td><button className="ref-id" type="button" onClick={() => setExpanded((current) => current === row.id ? null : row.id)}>{recallId(row.id)}</button></td><td><Link to={`/loans/${row.loanId}`}>{loanId(row.loanId)}</Link></td><td>{row.borrowerName ?? '—'}</td><td><b>{row.securitySymbol ?? '—'}</b>{row.securityName ? ` · ${row.securityName}` : ''}</td><td className="ref-num">{number(row.quantity)}</td><td className="ref-num">{number(row.fulfilledQuantity)}</td><td className="ref-num">{number(row.remainingQuantity)}</td><td className="ref-num">{number(row.pendingReturnQuantity)}</td><td>{stamp(row.dueAt)}</td><td><State value={row.status} /></td><td><button className="lifecycle-manage" type="button" onClick={() => setExpanded(row.id)}>{open.has(row.status) ? 'Track' : 'View'}</button></td></tr>{expanded === row.id && <tr className="ref-child"><td colSpan="12"><div className="live-return-details"><ReferenceTable title={`⚑ Recall summary — ${recallId(row.id)}`} headers={['Field', 'Value']} rows={[["Loan", loanId(row.loanId)], ["Borrower", row.borrowerName ?? '—'], ["Security", `${row.securitySymbol ?? '—'}${row.securityName ? ` · ${row.securityName}` : ''}`], ["Recall quantity", number(row.quantity)], ["Fulfilled quantity", number(row.fulfilledQuantity)], ["Remaining quantity", number(row.remainingQuantity)], ["Pending borrower returns", number(row.pendingReturnQuantity)], ["Return by IST", stamp(row.dueAt)], ["Reason", row.reason], ["Status", <State value={row.status} />]]} /><div className="live-margin-actions"><p>{open.has(row.status) ? 'The borrower must submit a linked return request. Confirm receipt in the Returns screen; the lender service automatically applies that settled return to this recall.' : 'This recall has no remaining lender action.'}</p><div><Link className="ref-clear" to={`/loans/${row.loanId}`}>Open loan</Link>{open.has(row.status) && <Link className="ref-primary" to="/returns">Review returns</Link>}</div></div></div></td></tr>}</Fragment>)}{!filtered.length && <tr><td colSpan="12" className="ref-empty">No live recalls match this view.</td></tr>}</tbody></table></div>}
  </section>
}
