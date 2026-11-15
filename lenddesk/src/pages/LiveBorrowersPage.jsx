import { Fragment, useEffect, useState } from 'react'
import { lenderGet, lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import ReferenceTable, { Badge } from '../components/reference/ReferenceTable'
import { useReferenceSort } from '../hooks/useReferenceSort'
import './ReferencePages.css'

const label = value => value == null ? '—' : String(value).toLowerCase().replaceAll('_', ' ').replace(/^./, value => value.toUpperCase())
const timestamp = value => value ? new Date(value).toLocaleString('en-GB', { timeZone: 'UTC' }) : '—'
const number = value => value == null ? '—' : Number(value).toLocaleString('en-IN')
const money = value => value == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(value)
const tone = value => /ACTIVE|SETTLED|PAID|FULFILLED/.test(value ?? '') ? 'good' : /REJECTED|CLOSED|EXPIRED|OVERDUE/.test(value ?? '') ? 'risk' : 'warn'

function NewBorrowerDialog({ organizationId, onClose, onCreated }) {
  const [form, setForm] = useState({ name: '', externalId: '', borrowerOrganizationId: '' })
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const update = (key, value) => setForm(current => ({ ...current, [key]: value }))
  async function submit(event) {
    event.preventDefault()
    setError('')
    setSaving(true)
    try {
      const borrower = await lenderPost(`/api/v1/organizations/${organizationId}/borrowers`, {
        name: form.name.trim(), externalId: form.externalId.trim(), borrowerOrganizationId: Number(form.borrowerOrganizationId),
      })
      onCreated(borrower)
    } catch (requestError) { setError(requestError.message) } finally { setSaving(false) }
  }
  return <div className="agreement-offer-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !saving) onClose() }}><section className="borrower-connect-dialog" role="dialog" aria-modal="true" aria-labelledby="connect-borrower-title"><header><div><p>COUNTERPARTY DIRECTORY</p><h2 id="connect-borrower-title">Connect borrower institution</h2></div><button type="button" className="agreement-offer-close" onClick={onClose} disabled={saving} aria-label="Close">×</button></header><p>The external ID and borrower organization ID must match the borrower service. Connecting also sends the lender reference through the existing FIX/Kafka channel.</p><form onSubmit={submit}><label>Institution name<input value={form.name} maxLength="160" onChange={event => update('name', event.target.value)} placeholder="Example Borrower LLP" required /></label><label>Borrower external ID<input value={form.externalId} maxLength="100" onChange={event => update('externalId', event.target.value)} placeholder="BORROWER_DEMO" required /></label><label>Borrower organization ID<input type="number" min="1" step="1" value={form.borrowerOrganizationId} onChange={event => update('borrowerOrganizationId', event.target.value)} placeholder="1" required /></label>{error && <p className="live-error" role="alert">{error}</p>}<footer><button type="button" onClick={onClose} disabled={saving}>Cancel</button><button className="ref-primary" type="submit" disabled={saving}>{saving ? 'Connecting…' : 'Connect borrower'}</button></footer></form></section></div>
}

function BorrowerDetails({ organizationId, id, refreshKey }) {
  const [state, setState] = useState({ scope: '', data: null, error: '' })
  const scope = `${organizationId}:${id}:${refreshKey}`
  useEffect(() => {
    const controller = new AbortController()
    lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/borrowers/${id}`, controller.signal)
      .then(data => { if (!controller.signal.aborted) setState({ scope, data, error: '' }) })
      .catch(error => { if (!controller.signal.aborted) setState({ scope, data: null, error: error.message }) })
    return () => controller.abort()
  }, [organizationId, id, refreshKey, scope])
  if (state.scope !== scope) return <p className="ref-empty" role="status">Loading borrower details…</p>
  if (state.error) return <p className="live-error" role="alert">Borrower details unavailable: {state.error}</p>
  const { borrower, agreements, loans, marginCalls, recalls } = state.data
  return <div className="live-borrower-details"><section className="live-borrower-strip"><div><small>External ID</small><strong>{borrower.externalId}</strong></div><div><small>Borrower organization</small><strong>{borrower.borrowerOrganizationId}</strong></div><div><small>Connection state</small><Badge tone={borrower.active ? 'good' : 'risk'}>{borrower.active ? 'Active' : 'Inactive'}</Badge></div><div><small>Connected UTC</small><strong>{timestamp(borrower.createdAt)}</strong></div></section><div className="live-borrower-tables"><ReferenceTable emptyMessage="No MSLA offers or agreements." title={`MSLAs (${agreements.length})`} headers={['Number', 'Status', 'Effective / expiry', 'Credit limit', 'Active loans']} rows={agreements.map(a => [a.number, <Badge tone={tone(a.status)}>{label(a.status)}</Badge>, `${a.effectiveDate} – ${a.expiryDate}`, money(a.creditLimit), number(a.activeLoanCount)])}/><ReferenceTable emptyMessage="No loan activity." title={`Loans (${loans.length})`} headers={['Loan ID', 'MSLA ID', 'Security ID', 'Outstanding', 'Status']} rows={loans.map(loan => [loan.id, loan.mslaId, loan.securityId, number(loan.outstandingQuantity), <Badge tone={tone(loan.status)}>{label(loan.status)}</Badge>])}/><ReferenceTable emptyMessage="No open risk obligations." title={`Risk obligations (${marginCalls.length + recalls.length})`} headers={['Type / ID', 'Loan', 'Amount / quantity', 'Due UTC', 'Status']} rows={[...marginCalls.map(call => [`Margin ${call.id}`, call.loanId, money(call.amount), timestamp(call.dueAt), <Badge tone={tone(call.status)}>{label(call.status)}</Badge>]), ...recalls.map(call => [`Recall ${call.id}`, call.loanId, number(call.quantity), timestamp(call.dueAt), <Badge tone={tone(call.status)}>{label(call.status)}</Badge>])]}/></div></div>
}

const columns = [['name', 'Borrower'], ['externalId', 'External ID'], ['borrowerOrganizationId', 'Borrower org ID'], ['active', 'Connection'], ['createdAt', 'Connected UTC']]

export default function LiveBorrowersPage() {
  const { organizationId, refreshKey, refresh } = useDataSource()
  const [state, setState] = useState({ scope: '', rows: [], error: '' })
  const [expanded, setExpanded] = useState(null)
  const [dialogOpen, setDialogOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => {
    const controller = new AbortController()
    lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/borrowers?page=0&size=200`, controller.signal)
      .then(data => { if (!controller.signal.aborted) setState({ scope, rows: data.content ?? [], error: '' }) })
      .catch(error => { if (!controller.signal.aborted) setState({ scope, rows: [], error: error.message }) })
    return () => controller.abort()
  }, [organizationId, refreshKey, scope])
  const loading = state.scope !== scope
  const { sorted, sortHeading } = useReferenceSort(state.rows)
  function cell(row, key) {
    if (key === 'name') return <button type="button" className="ref-id" onClick={() => setExpanded(current => current === row.id ? null : row.id)}>{row.name}</button>
    if (key === 'active') return <Badge tone={row.active ? 'good' : 'risk'}>{row.active ? 'Active' : 'Inactive'}</Badge>
    if (key === 'createdAt') return timestamp(row.createdAt)
    return row[key] ?? '—'
  }
  return <section className="ref-page borrower-reference live-borrowers-page"><header className="ref-heading"><h1>Borrowers &amp; Counterparties</h1><span className="ref-active-count">{loading ? 'Loading…' : `${state.rows.filter(row => row.active).length} connected`}</span><button className="ref-primary" type="button" onClick={() => { setNotice(''); setDialogOpen(true) }} disabled={loading || Boolean(state.error)}>Add borrower</button></header>{notice && <p className="live-agreement-notice" role="status">{notice}</p>}{loading ? <p className="ref-empty" role="status">Loading connected borrowers…</p> : state.error ? <p className="live-error" role="alert">Borrower directory unavailable: {state.error}</p> : <div className="ref-scroll"><table className="ref-main-table borrower-reference-table"><thead><tr><th aria-label="Expand borrower" />{columns.map(([key, title]) => sortHeading(key, title))}</tr></thead><tbody>{sorted.map(row => <Fragment key={row.id}><tr className={expanded === row.id ? 'ref-selected' : ''}><td><button type="button" className="ref-expander" aria-label={`${expanded === row.id ? 'Collapse' : 'Expand'} ${row.name}`} aria-expanded={expanded === row.id} onClick={() => setExpanded(current => current === row.id ? null : row.id)}>{expanded === row.id ? '⌄' : '›'}</button></td>{columns.map(([key]) => <td key={key}>{cell(row, key)}</td>)}</tr>{expanded === row.id && <tr className="ref-child"><td colSpan={columns.length + 1}><BorrowerDetails organizationId={organizationId} id={row.id} refreshKey={refreshKey} /></td></tr>}</Fragment>)}{sorted.length === 0 && <tr><td className="ref-empty" colSpan={columns.length + 1}>No borrowers are connected to this lender organization.</td></tr>}</tbody></table></div>}{dialogOpen && <NewBorrowerDialog organizationId={organizationId} onClose={() => setDialogOpen(false)} onCreated={borrower => { setDialogOpen(false); setNotice(`${borrower.name} was connected. The lender reference has been sent to the borrower.`); refresh() }} />}</section>
}
