import { Fragment, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { counterparties } from '../data/borrowerData'

const statusTone = (status) => ({ Approved: 'good', 'Pending review': 'warn', Restricted: 'risk', Active: 'good', 'Under review': 'warn', Suspended: 'risk' }[status] ?? 'neutral')

export default function BorrowersPage() {
  const { searchTerm } = useOutletContext()
  const [status, setStatus] = useState('All statuses')
  const [selectedId, setSelectedId] = useState('CP-00017')
  const [expandedId, setExpandedId] = useState('CP-00017')
  const rows = useMemo(() => counterparties.filter((counterparty) => (status === 'All statuses' || counterparty.status === status) && Object.values(counterparty).join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [status, searchTerm])

  function selectCounterparty(id) {
    setSelectedId(id)
    setExpandedId((current) => current === id ? null : id)
  }

  return <>
    <section className="heading borrower-heading"><h1>Borrowers &amp; Counterparties</h1><div><span>Approved <b>31</b></span><span>Aggregate limit <b>$4.50B</b></span><span>Limit utilised <b>40.9%</b></span><span className="critical">1 review overdue</span></div><button className="primary-action" type="button">+ Onboard counterparty</button></section>
    <section className="counterparty-filters panel"><div><label>Counterparty<input placeholder="Search counterparty, LEI, agreement or contact" /></label><label>Region<select><option>All regions</option><option>Americas</option><option>EMEA</option><option>APAC</option></select></label><label>Approval status<select value={status} onChange={(event) => setStatus(event.target.value)}><option>All statuses</option><option>Approved</option><option>Pending review</option><option>Restricted</option></select></label></div><div><button className="link" type="button" onClick={() => setStatus('All statuses')}>Clear filters</button><button className="outline-action" type="button">Export counterparties</button></div></section>
    <section className="counterparty-workspace"><article className="panel counterparty-register"><div className="panel-title"><h2>Counterparty register <small>{rows.length} shown</small></h2><small>Expand a counterparty to view linked agreements</small></div><div className="scroll"><table><thead><tr><th /><th>Counterparty</th><th>LEI identifier</th><th>Classification</th><th>Region</th><th>Rating</th><th>Credit limit</th><th>Utilised</th><th>Exposure</th><th>Coverage</th><th>Status</th><th>Next review</th></tr></thead><tbody>{rows.map((counterparty) => <Fragment key={counterparty.id}><tr className={selectedId === counterparty.id ? 'selected' : ''} onClick={() => selectCounterparty(counterparty.id)}><td><button className="row-expander" type="button" aria-label={`${expandedId === counterparty.id ? 'Collapse' : 'Expand'} ${counterparty.name}`}>{expandedId === counterparty.id ? '⌄' : '›'}</button></td><td><a>{counterparty.name}</a><small className="table-subline">{counterparty.id}</small></td><td className="mono">{counterparty.lei}</td><td>{counterparty.classification}</td><td>{counterparty.region}</td><td><b>{counterparty.rating}</b></td><td className="num">{counterparty.limit}</td><td><div className="utilisation"><span><i style={{ width: `${counterparty.utilisation}%` }} /></span><b>{counterparty.utilisation}%</b></div></td><td className="num">{counterparty.exposure}</td><td className="coverage">{counterparty.coverage}</td><td><StatusBadge tone={statusTone(counterparty.status)}>{counterparty.status}</StatusBadge></td><td className={counterparty.review === 'Review overdue' ? 'review-overdue' : ''}>{counterparty.review}</td></tr>{expandedId === counterparty.id && <tr className="child-row"><td colSpan="12"><div className="child-table counterparty-child"><div><b>Agreements with {counterparty.name}</b><span>{counterparty.agreements.length} agreement{counterparty.agreements.length === 1 ? '' : 's'} linked</span></div><table><thead><tr><th>MSLA ID</th><th>Framework</th><th>On-loan value</th><th>Active loans</th><th>Status</th></tr></thead><tbody>{counterparty.agreements.map((agreement) => <tr key={agreement[0]}><td><a>{agreement[0]}</a></td><td>{agreement[1]}</td><td className="num">{agreement[2]}</td><td className="num">{agreement[3]}</td><td><StatusBadge tone={statusTone(agreement[4])}>{agreement[4]}</StatusBadge></td></tr>)}</tbody></table></div></td></tr>}</Fragment>)}</tbody></table></div><footer>Showing {rows.length} of 31 approved counterparties · Exposure is refreshed from end-of-day collateral valuations</footer></article></section>
  </>
}
