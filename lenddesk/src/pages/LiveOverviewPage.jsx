import { useEffect, useMemo, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { lenderGet } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'

const label = value => value == null ? '—' : String(value).toLowerCase().replaceAll('_', ' ').replace(/^./, item => item.toUpperCase())
const number = value => value == null ? '—' : Number(value).toLocaleString('en-IN')
const money = value => value == null ? '—' : new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(value)
const time = value => value ? new Date(value).toLocaleString('en-GB', { timeZone: 'UTC', day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—'
const tone = value => /ACTIVE|SETTLED|PAID|FULFILLED/.test(value ?? '') ? 'good' : /OVERDUE|REJECTED|DEFAULT|CLOSED/.test(value ?? '') ? 'risk' : 'warn'

export default function LiveOverviewPage() {
  const { searchTerm = '' } = useOutletContext()
  const { organizationId, refreshKey } = useDataSource()
  const [tab, setTab] = useState('exceptions')
  const [state, setState] = useState({ scope: '', data: null, error: '' })
  const scope = `${organizationId}:${refreshKey}`
  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/dashboard`, controller.signal),
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/returns/overview?page=0&size=200`, controller.signal),
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/margin-calls/overview?page=0&size=200`, controller.signal),
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/recalls/overview?page=0&size=200`, controller.signal),
      lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/fix-monitor/overview?size=20`, controller.signal),
    ]).then(([dashboard, returns, margins, recalls, fix]) => {
      if (!controller.signal.aborted) setState({ scope, data: { dashboard, returns: returns.content ?? [], margins: margins.content ?? [], recalls: recalls.content ?? [], fix }, error: '' })
    }).catch(error => { if (!controller.signal.aborted) setState({ scope, data: null, error: error.message }) })
    return () => controller.abort()
  }, [organizationId, refreshKey, scope])
  const loading = state.scope !== scope
  const data = state.data
  const exceptions = useMemo(() => !data ? [] : [
    ...data.margins.filter(item => !['FULFILLED', 'CANCELLED'].includes(item.status)).map(item => ({ kind: 'Margin call', id: item.id, loan: item.loanId, borrower: item.borrowerName, security: item.securitySymbol, value: money(item.remainingAmount), due: item.dueAt, status: item.status })),
    ...data.recalls.filter(item => !['FULFILLED', 'CANCELLED'].includes(item.status)).map(item => ({ kind: 'Recall', id: item.id, loan: item.loanId, borrower: item.borrowerName, security: item.securitySymbol, value: number(item.remainingQuantity), due: item.dueAt, status: item.status })),
    ...data.returns.filter(item => item.status === 'PENDING').map(item => ({ kind: 'Return settlement', id: item.id, loan: item.loanId, borrower: item.borrowerName, security: item.securitySymbol, value: number(item.quantity), due: item.createdAt, status: item.status })),
  ].filter(item => `${item.kind} ${item.borrower} ${item.security} ${item.loan}`.toLowerCase().includes(searchTerm.toLowerCase())), [data, searchTerm])
  const settlements = useMemo(() => !data ? [] : data.returns.filter(item => ['PENDING', 'SETTLED'].includes(item.status)).filter(item => `${item.borrowerName} ${item.securitySymbol} ${item.loanId}`.toLowerCase().includes(searchTerm.toLowerCase())), [data, searchTerm])
  if (loading) return <p className="ref-empty" role="status">Loading live operations overview…</p>
  if (state.error) return <p className="live-error" role="alert">Live overview unavailable: {state.error}</p>
  const dashboard = data.dashboard
  const fixMessages = data.fix.messages ?? []
  return <><section className="heading"><h1>Operations Overview</h1><div><span>Active loans <b>{number(dashboard.activeLoans)}</b></span><span>On-loan value <b>{money(dashboard.onLoanValue)}</b></span><span>Available inventory <b>{number(dashboard.availableQuantity)}</b></span><span className={exceptions.length ? 'critical' : ''}>{exceptions.length} open exceptions</span></div></section><section className="operations-panel panel"><div className="panel-title"><div className="table-tabs" role="tablist" aria-label="Operational work queues"><button className={tab === 'exceptions' ? 'active' : ''} type="button" onClick={() => setTab('exceptions')}>Operational exceptions <StatusBadge tone={exceptions.length ? 'risk' : 'good'}>{exceptions.length} open</StatusBadge></button><button className={tab === 'settlements' ? 'active' : ''} type="button" onClick={() => setTab('settlements')}>Return settlements <StatusBadge tone="neutral">{settlements.length} records</StatusBadge></button></div><small>Live lender-service data · Organization {organizationId}</small></div>{tab === 'exceptions' ? <><div className="scroll"><table><thead><tr><th>Type</th><th>Borrower</th><th>Loan</th><th>Security</th><th>Open amount / quantity</th><th>Due UTC</th><th>Status</th></tr></thead><tbody>{exceptions.map(row => <tr key={`${row.kind}-${row.id}`}><td><span className="type">{row.kind}</span></td><td>{row.borrower ?? '—'}</td><td><Link to="/loans">{row.loan}</Link></td><td>{row.security ?? '—'}</td><td className="num">{row.value}</td><td>{time(row.due)}</td><td><StatusBadge tone={tone(row.status)}>{label(row.status)}</StatusBadge></td></tr>)}{!exceptions.length && <tr><td className="ref-empty" colSpan="7">No open operational exceptions.</td></tr>}</tbody></table></div><footer>Margin calls, recalls, and pending returns from lender-service.</footer></> : <><div className="scroll"><table><thead><tr><th>Return ID</th><th>Borrower</th><th>Loan</th><th>Security</th><th>Quantity</th><th>Submitted UTC</th><th>Status</th></tr></thead><tbody>{settlements.map(row => <tr key={row.id}><td>{row.id}</td><td>{row.borrowerName ?? '—'}</td><td><Link to="/loans">{row.loanId}</Link></td><td>{row.securitySymbol ?? '—'}</td><td className="num">{number(row.quantity)}</td><td>{time(row.createdAt)}</td><td><StatusBadge tone={tone(row.status)}>{label(row.status)}</StatusBadge></td></tr>)}{!settlements.length && <tr><td className="ref-empty" colSpan="7">No return settlement records.</td></tr>}</tbody></table></div><footer>Current and completed borrower loan-return instructions.</footer></>}</section><section className="bottom-grid"><article className="panel"><div className="panel-title"><h2>Lifecycle timeline</h2><small>{dashboard.recentNotifications.length} recent events</small></div><ol>{dashboard.recentNotifications.map(item => <li key={item.id}><time>{time(item.createdAt)}</time><i /><div><span className="type">{label(item.type)}</span><b>{item.message}</b><small>Reference {item.reference}</small></div><StatusBadge tone={item.isRead ? 'neutral' : 'warn'}>{item.isRead ? 'Read' : 'New'}</StatusBadge></li>)}{!dashboard.recentNotifications.length && <li><div><b>No lifecycle notifications</b><small>Events will appear here as workflows progress.</small></div></li>}</ol></article><article className="panel"><div className="panel-title"><h2>Recent FIX activity</h2><Link className="link" to="/fix-monitor">View FIX monitor</Link></div><div className="scroll"><table><thead><tr><th>UTC time</th><th>Direction</th><th>Message</th><th>Reference</th><th>State</th></tr></thead><tbody>{fixMessages.map(item => <tr key={item.id}><td>{time(item.createdAt)}</td><td className="direction">{item.direction}</td><td>{label(item.messageType)}</td><td>{item.reference}</td><td><StatusBadge tone={tone(item.state)}>{label(item.state)}</StatusBadge></td></tr>)}{!fixMessages.length && <tr><td className="ref-empty" colSpan="5">No FIX activity recorded.</td></tr>}</tbody></table></div></article></section></>
}
