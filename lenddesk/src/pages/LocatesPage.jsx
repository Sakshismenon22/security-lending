import { Fragment, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { locateRequests } from '../data/locateData'

const toneForStatus = (status) => ({ 'Pending decision': 'warn', Approved: 'good', 'Partially approved': 'warn', Declined: 'risk', Reserved: 'neutral' }[status] ?? 'neutral')

export default function LocatesPage() {
  const { searchTerm } = useOutletContext()
  const [tab, setTab] = useState('All requests')
  const [selectedId, setSelectedId] = useState('LOC-000491')
  const [expandedId, setExpandedId] = useState('LOC-000491')
  const [filter, setFilter] = useState('All decisions')
  const rows = useMemo(() => locateRequests.filter((locate) => {
    const matchesTab = tab === 'All requests' || (tab === 'Awaiting decision' && locate.status === 'Pending decision') || (tab === 'Allocated today' && ['Approved', 'Partially approved', 'Reserved'].includes(locate.status))
    const matchesFilter = filter === 'All decisions' || locate.status === filter
    return matchesTab && matchesFilter && Object.values(locate).join(' ').toLowerCase().includes(searchTerm.toLowerCase())
  }), [tab, filter, searchTerm])

  function selectLocate(id) {
    setSelectedId(id)
    setExpandedId((current) => current === id ? null : id)
  }

  return <>
    <section className="heading locate-heading"><h1>Locate Requests</h1><div><span>Pending decision <b>6</b></span><span>Allocated today <b>1.24M</b></span><span>Reservation expiry <b>15:00 UTC</b></span><span className="critical">2 high priority</span></div><button className="primary-action" type="button">Availability broadcast</button></section>
    <section className="locate-toolbar panel"><div className="table-tabs" role="tablist" aria-label="Locate request view"><button type="button" className={tab === 'All requests' ? 'active' : ''} onClick={() => setTab('All requests')}>All requests <StatusBadge tone="neutral">24</StatusBadge></button><button type="button" className={tab === 'Awaiting decision' ? 'active' : ''} onClick={() => setTab('Awaiting decision')}>Awaiting decision <StatusBadge tone="warn">6</StatusBadge></button><button type="button" className={tab === 'Allocated today' ? 'active' : ''} onClick={() => setTab('Allocated today')}>Allocated today <StatusBadge tone="good">18</StatusBadge></button></div><div><select value={filter} onChange={(event) => setFilter(event.target.value)}><option>All decisions</option><option>Pending decision</option><option>Approved</option><option>Partially approved</option><option>Reserved</option><option>Declined</option></select><button className="link" type="button" onClick={() => setFilter('All decisions')}>Clear filters</button><button className="outline-action" type="button">Export queue</button></div></section>
    <section className="locate-workspace"><article className="panel locate-register"><div className="panel-title"><h2>Locate queue <small>{rows.length} requests shown</small></h2><small>Expand a request to review inventory availability and allocation</small></div><div className="scroll"><table><thead><tr><th /><th>Locate ID</th><th>Received UTC</th><th>Borrower</th><th>MSLA</th><th>Security</th><th>Requested</th><th>Available</th><th>Allocated</th><th>Indicative rate</th><th>Expiry</th><th>Priority</th><th>Status</th></tr></thead><tbody>{rows.map((locate) => <Fragment key={locate.id}><tr className={selectedId === locate.id ? 'selected' : ''} onClick={() => selectLocate(locate.id)}><td><button className="row-expander" type="button" aria-label={`${expandedId === locate.id ? 'Collapse' : 'Expand'} ${locate.id}`}>{expandedId === locate.id ? '⌄' : '›'}</button></td><td><a>{locate.id}</a></td><td>{locate.received}</td><td><strong>{locate.borrower}</strong></td><td><a>{locate.msla}</a></td><td><b>{locate.symbol}</b><small className="table-subline mono">{locate.isin}</small></td><td className="num">{locate.requested}</td><td className="num">{locate.available}</td><td className="num"><b>{locate.allocated}</b></td><td>{locate.rate}</td><td className={locate.urgency === 'High' ? 'review-overdue' : ''}>{locate.expiry}</td><td><StatusBadge tone={locate.urgency === 'High' ? 'risk' : 'neutral'}>{locate.urgency}</StatusBadge></td><td><StatusBadge tone={toneForStatus(locate.status)}>{locate.status}</StatusBadge></td></tr>{expandedId === locate.id && <tr className="child-row"><td colSpan="13"><div className="child-table locate-child"><div><b>Allocation review · {locate.id}</b><span>{locate.notes}</span></div><table><thead><tr><th>Custody source</th><th>Available balance</th><th>Proposed allocation</th><th>Reservation state</th></tr></thead><tbody>{locate.inventory.map((lot) => <tr key={lot[0]}><td>{lot[0]}</td><td className="num">{lot[1]}</td><td className="num"><b>{lot[2]}</b></td><td><StatusBadge tone={locate.status === 'Declined' ? 'risk' : toneForStatus(locate.status)}>{locate.status === 'Reserved' ? 'Reserved' : 'Available'}</StatusBadge></td></tr>)}</tbody></table><div className="locate-actions"><span>Requested <b>{locate.requested}</b> · Proposed allocation <b>{locate.allocated}</b> · Expires <b>{locate.expiry}</b></span>{locate.status === 'Pending decision' && <div><button type="button" className="outline-action">Decline</button><button type="button" className="primary-action">Approve / reserve</button></div>}</div></div></td></tr>}</Fragment>)}</tbody></table></div><footer>Showing {rows.length} of 24 locate requests · Approved locates reserve inventory until the agreement expiry time</footer></article></section>
  </>
}
