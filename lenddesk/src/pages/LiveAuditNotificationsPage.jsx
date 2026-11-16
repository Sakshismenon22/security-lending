import PaginatedTable from '../components/PaginatedTable'
import { Fragment, useEffect, useMemo, useState } from 'react'
import { lenderGet, lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import ReferenceTable, { Badge } from '../components/reference/ReferenceTable'
import './ReferencePages.css'
import './MonitorLivePages.css'

const label = (value) => String(value ?? '—').toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
const stamp = (value) => value ? new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)).replace(',', '') : '—'
const tone = (value) => /unread|failed|rejected|overdue/i.test(value) ? 'risk' : /pending|new/i.test(value) ? 'warn' : 'good'

async function allEvents(path, signal) {
  const content = [];
  let page = 0, result;
  do {
    result = await lenderGet(`${path}?page=${page++}&size=200`, signal);
    content.push(...result.content);
  } while (page < result.totalPages);
  return { content };
}

export default function LiveAuditNotificationsPage() {
  const { organizationId, refreshKey, refresh } = useDataSource()
  const [state, setState] = useState({ scope: '', audits: [], notifications: [], error: '' })
  const [tab, setTab] = useState('All events')
  const [expanded, setExpanded] = useState(null)
  const [busy, setBusy] = useState(null)
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => { const abort = new AbortController(); Promise.all([allEvents(`/api/v1/organizations/${organizationId}/lenddesk/audit`, abort.signal), allEvents(`/api/v1/organizations/${organizationId}/lenddesk/notifications`, abort.signal)]).then(([audit, notifications]) => { if (!abort.signal.aborted) setState({ scope, audits: audit.content ?? [], notifications: notifications.content ?? [], error: '' }) }).catch((error) => { if (!abort.signal.aborted) setState({ scope, audits: [], notifications: [], error: error.message }) }); return () => abort.abort() }, [organizationId, refreshKey, scope])
  const rows = useMemo(() => state.scope === scope ? [...state.audits.map((item) => ({ ...item, key: `audit-${item.id}`, id: `AUD-${String(item.id).padStart(6, '0')}`, category: 'Audit event', source: 'Lender service', action: label(item.type), state: 'Recorded', detail: `Audit event recorded for reference ${item.reference}.` })), ...state.notifications.map((item) => ({ ...item, key: `notification-${item.id}`, id: `NTF-${String(item.id).padStart(6, '0')}`, category: 'Notification', source: 'Lender service', action: label(item.type), state: item.isRead ? 'Read' : 'Unread', detail: item.message, notification: true }))].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt)) : [], [scope, state.audits, state.notifications, state.scope])
  const filtered = rows.filter((row) => tab === 'All events' || (tab === 'Notifications' && row.notification) || (tab === 'Audit events' && !row.notification))
  async function markRead(row) { if (!row.notification || row.isRead || busy) return; setBusy(row.key); try { await lenderPost(`/api/v1/organizations/${organizationId}/notifications/${row.id.replace('NTF-', '')}/read`, {}); refresh() } finally { setBusy(null) } }
  return <section className="ref-page monitor-page"><header className="monitor-heading"><div><h1>Audit &amp; Notifications </h1><p>Organization event trail, lifecycle notifications and operational evidence.</p></div><button className="ref-primary" type="button" onClick={refresh}>Refresh live data</button></header><section className="monitor-metrics"><div><small>Audit events</small><strong>{state.audits.length}</strong></div><div><small>Notifications</small><strong>{state.notifications.length}</strong></div><div><small>Unread alerts</small><strong className={state.notifications.some((item) => !item.isRead) ? 'risk-text' : ''}>{state.notifications.filter((item) => !item.isRead).length}</strong></div><div><small>Retention</small><strong>7 years</strong></div></section><nav className="monitor-tabs" role="tablist">{['All events', 'Notifications', 'Audit events'].map((item) => <button key={item} type="button" className={tab === item ? 'active' : ''} onClick={() => setTab(item)}>{item}</button>)}</nav>{state.scope !== scope ? <p className="ref-empty">Loading audit and notification data…</p> : state.error ? <p className="live-error">Audit data unavailable: {state.error}</p> : <PaginatedTable className="ref-scroll" label="Audit and notifications"><table className="ref-main-table monitor-table"><thead><tr><th/><th>Event ID</th><th>UTC time</th><th>Category</th><th>Actor / source</th><th>Action</th><th>Reference</th><th>State</th><th>Action</th></tr></thead><tbody>{filtered.map((row) => <Fragment key={row.key}><tr className={expanded === row.key ? 'ref-selected' : ''}><td><button className="ref-expander" type="button" onClick={() => setExpanded((current) => current === row.key ? null : row.key)}>{expanded === row.key ? '⌄' : '›'}</button></td><td><button type="button" className="ref-id" onClick={() => setExpanded((current) => current === row.key ? null : row.key)}>{row.id}</button></td><td>{stamp(row.createdAt)}</td><td>{row.category}</td><td>{row.source}</td><td>{row.action}</td><td>{row.reference}</td><td><Badge tone={tone(row.state)}>{row.state}</Badge></td><td>{row.notification && !row.isRead ? <button className="lifecycle-manage" disabled={busy === row.key} type="button" onClick={() => markRead(row)}>{busy === row.key ? 'Saving…' : 'Mark read'}</button> : '—'}</td></tr>{expanded === row.key && <tr className="ref-child"><td colSpan="9"><div className="monitor-detail"><ReferenceTable title={`Event detail — ${row.id}`} headers={['Field', 'Value']} rows={[["Category", row.category], ["Action", row.action], ["Reference", row.reference], ["Recorded UTC", stamp(row.createdAt)], ["Detail", row.detail]]} /></div></td></tr>}</Fragment>)}{!filtered.length && <tr><td colSpan="9" className="ref-empty">No events match this view.</td></tr>}</tbody></table></PaginatedTable>}</section>
}
