import { Fragment, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { inventory } from '../data/inventoryData'

const inventoryTone = (status) => ({ Available: 'good', Tight: 'warn', 'Hard to borrow': 'risk', Restricted: 'neutral' }[status] ?? 'neutral')

export default function InventoryPage() {
  const { searchTerm } = useOutletContext()
  const [availability, setAvailability] = useState('All availability')
  const [selectedId, setSelectedId] = useState('INV-00041')
  const [expandedId, setExpandedId] = useState('INV-00041')
  const rows = useMemo(() => inventory.filter((security) => (availability === 'All availability' || security.status === availability) && Object.values(security).join(' ').toLowerCase().includes(searchTerm.toLowerCase())), [availability, searchTerm])

  function selectSecurity(id) {
    setSelectedId(id)
    setExpandedId((current) => current === id ? null : id)
  }

  return <>
    <section className="heading inventory-heading"><h1>Lendable Inventory</h1><div><span>Gross inventory <b>$4.76B</b></span><span>Available to lend <b>$1.89B</b></span><span>On loan <b>$2.77B</b></span><span className="critical">2 hard-to-borrow</span></div><button className="primary-action" type="button">Import inventory</button></section>
    <section className="inventory-filters panel"><div><label>Security<input placeholder="Search symbol, ISIN or security name" /></label><label>Asset class<select><option>All asset classes</option><option>US Equity</option><option>US Treasury</option><option>ETF</option></select></label><label>Availability<select value={availability} onChange={(event) => setAvailability(event.target.value)}><option>All availability</option><option>Available</option><option>Tight</option><option>Hard to borrow</option></select></label><label>Custodian<select><option>All custodians</option><option>DTC 2191</option><option>State Street 447</option><option>BNY Mellon 008</option></select></label></div><div><button className="link" type="button" onClick={() => setAvailability('All availability')}>Clear filters</button><button className="outline-action" type="button">Export availability</button></div></section>
    <section className="inventory-workspace"><article className="panel inventory-register"><div className="panel-title"><h2>Availability register <small>{rows.length} securities shown</small></h2><small>Expand a security to view custody lots and lendable balances</small></div><div className="scroll"><table><thead><tr><th /><th>Security</th><th>ISIN</th><th>Asset class</th><th>Total inventory</th><th>On loan</th><th>Reserved</th><th>Available</th><th>Availability</th><th>Market price</th><th>Market value</th><th>Indicative rate</th><th>Status</th></tr></thead><tbody>{rows.map((security) => <Fragment key={security.id}><tr className={selectedId === security.id ? 'selected' : ''} onClick={() => selectSecurity(security.id)}><td><button className="row-expander" type="button" aria-label={`${expandedId === security.id ? 'Collapse' : 'Expand'} ${security.symbol}`}>{expandedId === security.id ? '⌄' : '›'}</button></td><td><a>{security.symbol}</a><small className="table-subline">{security.name}</small></td><td className="mono">{security.isin}</td><td>{security.assetClass}</td><td className="num">{security.total}</td><td className="num">{security.onLoan}</td><td className="num">{security.reserved}</td><td className="num"><b>{security.available}</b></td><td><div className="utilisation"><span><i style={{ width: `${security.utilisation}%` }} /></span><b>{security.utilisation}%</b></div></td><td className="num">{security.price}</td><td className="num">{security.marketValue}</td><td>{security.rate}</td><td><StatusBadge tone={inventoryTone(security.status)}>{security.status}</StatusBadge></td></tr>{expandedId === security.id && <tr className="child-row"><td colSpan="13"><div className="child-table inventory-child"><div><b>Custody lots for {security.symbol}</b><span>{security.lots.length} sources · {security.available} shares available</span></div><table><thead><tr><th>Lot ID</th><th>Custodian account</th><th>Settled balance</th><th>On loan</th><th>Available to lend</th><th>Availability</th></tr></thead><tbody>{security.lots.map((lot) => <tr key={lot[0]}><td><a>{lot[0]}</a></td><td>{lot[1]}</td><td className="num">{lot[2]}</td><td className="num">{lot[3]}</td><td className="num"><b>{lot[4]}</b></td><td><StatusBadge tone={inventoryTone(lot[5])}>{lot[5]}</StatusBadge></td></tr>)}</tbody></table></div></td></tr>}</Fragment>)}</tbody></table></div><footer>Showing {rows.length} of 1,284 lendable securities · Availability equals settled inventory less outstanding loans and active reservations</footer></article></section>
  </>
}
