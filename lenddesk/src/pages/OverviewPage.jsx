import { useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { exceptions, fixRows, settlements, timeline } from '../data/overviewData'

const TableLink = ({ children }) => <a>{children}</a>

export default function OverviewPage() {
  const { searchTerm } = useOutletContext()
  const [priority, setPriority] = useState('All priorities')
  const [selected, setSelected] = useState('LOAN-000006')
  const [operationsView, setOperationsView] = useState('exceptions')
  const rows = useMemo(() => exceptions.filter((row) => (priority === 'All priorities' || row[0] === priority) && row.join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [priority, searchTerm])
  const settlementRows = useMemo(() => settlements.filter((row) => row.join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [searchTerm])
  return <>
    <section className="heading"><h1>Operations Overview</h1><div><span>Active loans <b>342</b></span><span>On-loan value <b>$1.84B</b></span><span>Collateralization <b>104.2%</b></span><span className="critical">3 open exceptions</span></div></section>
    <section className="operations-panel panel">
      <div className="panel-title">
        <div className="table-tabs" role="tablist" aria-label="Operational work queues">
          <button className={operationsView === 'exceptions' ? 'active' : ''} type="button" role="tab" aria-selected={operationsView === 'exceptions'} onClick={() => setOperationsView('exceptions')}>Operational exceptions <StatusBadge tone="risk">3 open</StatusBadge></button>
          <button className={operationsView === 'settlements' ? 'active' : ''} type="button" role="tab" aria-selected={operationsView === 'settlements'} onClick={() => setOperationsView('settlements')}>Today’s settlements <StatusBadge tone="neutral">12 due</StatusBadge></button>
        </div>
        {operationsView === 'exceptions' ? <div><select value={priority} onChange={(event) => setPriority(event.target.value)}><option>All priorities</option><option>High</option><option>Medium</option><option>Low</option></select><button className="link" onClick={() => setPriority('All priorities')}>Reset filters</button></div> : <small>Queue: <b>12</b> · On time: 10 · <em>At risk: 2</em></small>}
      </div>
      {operationsView === 'exceptions' ? <><div className="scroll"><table><thead><tr><th>Priority</th><th>Event</th><th>Borrower</th><th>Loan</th><th>Security</th><th>Quantity</th><th>Exposure</th><th>Due UTC</th><th>Status</th></tr></thead><tbody>{rows.map((row) => <tr key={row[3] + row[1]} className={selected === row[3] ? 'selected' : ''} onClick={() => setSelected(row[3])}><td><StatusBadge tone={row[9]}>{row[0]}</StatusBadge></td><td>{row[1]}</td><td>{row[2]}</td><td><TableLink>{row[3]}</TableLink></td><td>{row[4]}</td><td className="num">{row[5]}</td><td className="num">{row[6]}</td><td>{row[7]}</td><td><StatusBadge tone={row[9]}>{row[8]}</StatusBadge></td></tr>)}</tbody></table></div><footer>Showing {rows.length} of 6 operational items · Select a row to inspect its linked loan</footer></> : <><div className="scroll"><table><thead><tr><th>Type</th><th>Loan</th><th>Security</th><th>Quantity</th><th>Amount</th><th>Due</th><th>Status</th></tr></thead><tbody>{settlementRows.map((row) => <tr key={row[1] + row[0]} className={selected === row[1] ? 'selected' : ''} onClick={() => setSelected(row[1])}><td><span className="type">{row[0]}</span></td><td><TableLink>{row[1]}</TableLink></td><td>{row[2]}</td><td className="num">{row[3]}</td><td className="num">{row[4]}</td><td>{row[5]}</td><td><StatusBadge tone={row[7]}>{row[6]}</StatusBadge></td></tr>)}</tbody></table></div><footer>Showing {settlementRows.length} of 12 settlement instructions · Select a row to inspect its linked loan</footer></>}
    </section>
    <section className="bottom-grid"><article className="panel"><div className="panel-title"><h2>Lifecycle timeline</h2><div><select><option>All event types</option></select><select><option>Next 24 hours</option></select></div></div><ol>{timeline.map((row) => <li key={row[0]}><time>{row[0]}</time><i /><div><span className="type">{row[1]}</span><b>{row[2]}</b><small>{row[3]}</small></div><StatusBadge tone={row[5]}>{row[4]}</StatusBadge></li>)}</ol></article>
    <article className="panel"><div className="panel-title"><h2>Recent FIX activity</h2><button className="link">View FIX monitor</button></div><div className="scroll"><table><thead><tr><th>UTC time</th><th>Direction</th><th>Message</th><th>Reference</th><th>Security</th><th>Details</th><th>Status</th></tr></thead><tbody>{fixRows.map((row) => <tr key={row[0]}><td>{row[0]}</td><td className="direction">{row[1]}</td><td>{row[2]}</td><td><TableLink>{row[3]}</TableLink></td><td>{row[4]}</td><td>{row[5]}</td><td><StatusBadge tone="good">Accepted</StatusBadge></td></tr>)}</tbody></table></div></article></section>
  </>
}
