import { useCallback, useEffect, useMemo, useState } from 'react'
import { Download, RefreshCw, Search, SlidersHorizontal, ExternalLink } from 'lucide-react'
import { tradingDeskApi, unwrapRows } from '../../api/tradingDeskApi'
import './Holdings.css'

const pick = (o, ...keys) => { for (const k of keys) { const value = o?.[k]; if (value !== undefined && value !== null && value !== '') return value } return null }
const arr = value => unwrapRows(value)
const num = value => value === null || value === undefined || value === '' || !Number.isFinite(Number(value)) ? 0 : Number(value)
const fmt = value => value === null || value === undefined || value === '' ? '—' : Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })
const money = value => value === null || value === undefined || value === '' ? '—' : '₹' + Number(value).toLocaleString('en-IN', { maximumFractionDigits: 2 })
const pct = value => value === null || value === undefined || value === '' ? '—' : (Number(value) > 0 ? '+' : '') + Number(value).toFixed(2) + '%'
const date = value => { if (!value) return '—'; const d = new Date(value); return Number.isNaN(+d) ? String(value) : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', timeZone: 'Asia/Kolkata' }) }
const securityOf = row => row?.security && typeof row.security === 'object' ? row.security : row
const securityKey = row => String(pick(row, 'securityId', 'security_id', 'id') ?? pick(securityOf(row), 'id') ?? '')
const statusTone = value => { const s = String(value || '').toLowerCase(); return /available|settled|complete/.test(s) ? 'green' : /active|deployed|on loan/.test(s) ? 'blue' : /pending|recall|return|restricted|overdue/.test(s) ? 'amber' : 'neutral' }

function normalizeHoldings(holdings, securities, positions) {
  const securityMap = new Map(securities.map(s => [String(pick(s, 'id')), s]))
  const positionMap = new Map(positions.map(p => [securityKey(p), p]))
  const result = []
  holdings.forEach((raw, index) => {
    const nested = securityOf(raw)
    const id = securityKey(raw)
    const security = securityMap.get(id) || nested
    const position = positionMap.get(id) || {}
    const symbol = pick(security, 'symbol', 'ticker') ?? pick(raw, 'symbol', 'securitySymbol', 'security_symbol') ?? '—'
    const source = String(pick(raw, 'source', 'ownership', 'holdingType', 'holding_type', 'positionType', 'position_type') ?? '').toUpperCase()
    const total = num(pick(raw, 'quantity', 'totalQuantity', 'total_quantity', 'netQuantity', 'net_quantity') ?? pick(position, 'quantity', 'net_quantity'))
    const owned = num(pick(raw, 'ownedQuantity', 'owned_quantity', 'longQuantity', 'long_quantity', 'availableOwnedQuantity', 'available_owned_quantity'))
    const borrowed = num(pick(raw, 'borrowedQuantity', 'borrowed_quantity', 'shortQuantity', 'short_quantity', 'availableBorrowedQuantity', 'available_borrowed_quantity'))
    const averageCost = pick(raw, 'averageCost', 'average_cost', 'avgCost', 'avg_cost', 'averagePrice', 'average_price') ?? pick(position, 'averagePrice', 'average_price', 'averageCost', 'average_cost')
    const price = pick(raw, 'lastPrice', 'last_price', 'ltp', 'price', 'currentPrice', 'current_price') ?? pick(security, 'price', 'lastPrice', 'last_price') ?? pick(position, 'price')
    const marketValue = pick(raw, 'marketValue', 'market_value', 'marketValueCr', 'market_value_cr')
    const pnl = pick(raw, 'unrealizedPnl', 'unrealized_pnl', 'pnl', 'profitLoss', 'profit_loss') ?? pick(position, 'unrealizedPnl', 'unrealized_pnl')
    const isin = pick(security, 'isin') ?? pick(raw, 'isin')
    const assetClass = pick(security, 'assetClass', 'asset_class', 'type') ?? pick(raw, 'assetClass', 'asset_class') ?? 'Equity'
    const common = { id: id || String(index), symbol, securityName: pick(security, 'name', 'securityName', 'security_name') ?? pick(raw, 'securityName', 'security_name', 'name') ?? symbol, isin, assetClass, averageCost, price, marketValue: marketValue ?? (price != null ? num(price) * total : null), pnl, raw, security, position }
    if (owned > 0 && borrowed > 0 && !source.includes('BORROW')) {\n      result.push({ ...common, key: id + '-owned-' + index, ownership: 'Owned', quantity: owned, marketValue: price != null ? num(price) * owned : (marketValue != null ? num(marketValue) * owned / Math.max(1, total) : null), available: pick(raw, 'availableOwnedQuantity', 'available_owned_quantity') ?? owned, borrowedQty: 0, status: pick(raw, 'status') ?? 'Available', lender: null, maturity: null })\n      result.push({ ...common, key: id + '-borrowed-' + index, ownership: 'Borrowed', quantity: borrowed, marketValue: price != null ? num(price) * borrowed : (marketValue != null ? num(marketValue) * borrowed / Math.max(1, total) : null), available: pick(raw, 'availableBorrowedQuantity', 'available_borrowed_quantity'), borrowedQty: borrowed, status: pick(raw, 'borrowStatus', 'borrow_status') ?? 'Active', lender: pick(raw, 'lenderName', 'lender_name'), maturity: pick(raw, 'maturityDate', 'maturity_date') })\n    } else if (source.includes('BORROW') || (borrowed > 0 && owned === 0)) {
      result.push({ ...common, key: id + '-borrowed-' + index, ownership: 'Borrowed', quantity: total || borrowed, available: pick(raw, 'availableQuantity', 'available_quantity') ?? pick(position, 'availableBorrowedQuantity', 'available_borrowed_quantity'), borrowedQty: total || borrowed, status: pick(raw, 'status', 'loanStatus', 'loan_status') ?? 'Active', lender: pick(raw, 'lenderName', 'lender_name'), maturity: pick(raw, 'maturityDate', 'maturity_date') })
    } else if (source.includes('OWN') || owned > 0 || (borrowed === 0 && total > 0)) {
      const qty = owned || total
      result.push({ ...common, key: id + '-owned-' + index, ownership: 'Owned', quantity: qty, available: pick(raw, 'availableQuantity', 'available_quantity', 'availableOwnedQuantity', 'available_owned_quantity') ?? pick(position, 'availableOwnedQuantity', 'available_owned_quantity') ?? qty, borrowedQty: pick(raw, 'borrowedQuantity', 'borrowed_quantity'), status: pick(raw, 'status') ?? 'Available', lender: null, maturity: null })
    } else {
      result.push({ ...common, key: id + '-holding-' + index, ownership: '—', quantity: total, available: pick(raw, 'availableQuantity', 'available_quantity'), borrowedQty: borrowed || null, status: pick(raw, 'status') ?? '—', lender: null, maturity: null })
    }
    if (source.includes('OWN') && borrowed > 0) result.push({ ...common, key: id + '-borrowed-' + index, ownership: 'Borrowed', quantity: borrowed, available: pick(raw, 'availableBorrowedQuantity', 'available_borrowed_quantity'), borrowedQty: borrowed, status: pick(raw, 'borrowStatus', 'borrow_status') ?? 'Active', lender: pick(raw, 'lenderName', 'lender_name'), maturity: pick(raw, 'maturityDate', 'maturity_date') })
  })
  return result
}

function DetailLine({ label, value, emphasis }) { return <div className="holding-detail-line"><span>{label}</span><b className={emphasis ? 'positive' : ''}>{value ?? '—'}</b></div> }

export default function Holdings() {
  const [data, setData] = useState({ holdings: [], positions: [], securities: [], trades: [], loans: [], dashboard: {}, account: {} })
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [search, setSearch] = useState('')
  const [ownershipFilter, setOwnershipFilter] = useState('All')
  const [assetFilter, setAssetFilter] = useState('All')
  const [statusFilter, setStatusFilter] = useState('All')
  const [tab, setTab] = useState('All Holdings')
  const [selectedKey, setSelectedKey] = useState(null)
  const [detailTab, setDetailTab] = useState('Overview')
  const refresh = useCallback(async () => {
    setLoading(true); setError('')
    const jobs = await Promise.allSettled([tradingDeskApi.holdings(), tradingDeskApi.positions(), tradingDeskApi.securities(''), tradingDeskApi.trades(), tradingDeskApi.loans(), tradingDeskApi.dashboard(), tradingDeskApi.account()])
    const names = ['holdings', 'positions', 'securities', 'trades', 'loans', 'dashboard', 'account']
    const next = { holdings: [], positions: [], securities: [], trades: [], loans: [], dashboard: {}, account: {} }
    jobs.forEach((job, i) => { if (job.status === 'fulfilled') next[names[i]] = i > 4 ? (job.value || {}) : arr(job.value) })
    if (jobs[0].status === 'rejected') setError(jobs[0].reason?.message || 'Unable to load holdings')
    setData(next); setSelectedKey(current => current || null); setLoading(false)
  }, [])
  useEffect(() => { refresh(); const timer = window.setInterval(refresh, 30000); return () => window.clearInterval(timer) }, [refresh])
  const rows = useMemo(() => normalizeHoldings(data.holdings.length ? data.holdings : data.positions, data.securities, data.positions), [data])
  const filtered = useMemo(() => rows.filter(row => {
    const q = search.trim().toLowerCase()
    if (q && ![row.symbol, row.securityName, row.isin, row.ownership, row.lender].join(' ').toLowerCase().includes(q)) return false
    if (tab === 'Owned Securities' && row.ownership !== 'Owned') return false
    if (tab === 'Borrowed Securities' && row.ownership !== 'Borrowed') return false
    if (ownershipFilter !== 'All' && row.ownership !== ownershipFilter) return false
    if (assetFilter !== 'All' && row.assetClass !== assetFilter) return false
    if (statusFilter !== 'All' && String(row.status).toLowerCase() !== statusFilter.toLowerCase()) return false
    return true
  }), [rows, search, tab, ownershipFilter, assetFilter, statusFilter])
  const selected = rows.find(row => row.key === selectedKey) || filtered[0] || rows[0] || null
  const totalMV = rows.reduce((sum, row) => sum + num(row.marketValue), 0)
  const ownedRows = rows.filter(row => row.ownership === 'Owned')
  const borrowedRows = rows.filter(row => row.ownership === 'Borrowed')
  const ownedMV = ownedRows.reduce((sum, row) => sum + num(row.marketValue), 0)
  const borrowedMV = borrowedRows.reduce((sum, row) => sum + num(row.marketValue), 0)
  const ownedQty = ownedRows.reduce((sum, row) => sum + num(row.quantity), 0)
  const borrowedQty = borrowedRows.reduce((sum, row) => sum + num(row.quantity), 0)
  const totalQty = ownedQty + borrowedQty
  const utilisation = totalQty ? borrowedQty / totalQty * 100 : 0
  const trades = data.trades.filter(trade => String(pick(trade, 'securityId', 'security_id')) === String(selected?.id))
  const loans = data.loans.filter(loan => String(pick(loan, 'securityId', 'security_id')) === String(selected?.id))
  const marketValueCr = selected?.marketValue != null ? num(selected.marketValue) / (num(selected.marketValue) > 10000000 ? 10000000 : 1) : null
  const priceChange = pick(selected?.raw, 'dayChangePercent', 'day_change_percent', 'changePercent', 'change_percent')
  const activity = [...trades.map(trade => ({ kind: 'Trade ' + String(pick(trade, 'side') || '').toUpperCase(), date: pick(trade, 'tradedAt', 'traded_at', 'createdAt', 'created_at'), qty: pick(trade, 'quantity'), price: pick(trade, 'price') })), ...loans.map(loan => ({ kind: 'Borrow', date: pick(loan, 'createdAt', 'created_at'), qty: pick(loan, 'quantity'), price: pick(loan, 'feeBps', 'fee_bps') }))].sort((a, b) => new Date(b.date || 0) - new Date(a.date || 0)).slice(0, 6)
  const exportCsv = () => { const columns = ['symbol', 'securityName', 'isin', 'assetClass', 'ownership', 'quantity', 'available', 'borrowedQty', 'averageCost', 'price', 'marketValue', 'pnl', 'status']; const quote = value => '"' + String(value ?? '').replaceAll('"', '""') + '"'; const csv = [columns.join(','), ...filtered.map(row => columns.map(key => quote(row[key])).join(','))].join('\r\n'); const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' })); const a = document.createElement('a'); a.href = url; a.download = 'borrowdesk-holdings.csv'; a.click(); URL.revokeObjectURL(url) }
  const assetOptions = [...new Set(rows.map(row => row.assetClass).filter(Boolean))]
  return <main className="holdings-page">
    <header className="holdings-heading"><div><h1>Holdings</h1><p>Securities owned and borrowed by your organization</p></div><div className="holdings-actions"><span>As of {new Date().toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })}</span><button title="Refresh" onClick={refresh}><RefreshCw size={14}/></button><button className="holdings-export" onClick={exportCsv}><Download size={13}/> Export</button></div></header>
    <section className="holdings-kpis">
      <div className="total"><span>Total Holdings (MV)</span><b>{money(totalMV)}</b></div><div><span>Owned (MV)</span><b>{money(ownedMV)}</b><small>{totalMV ? (ownedMV / totalMV * 100).toFixed(1) : '0.0'}% of total</small></div><div><span>Borrowed (MV)</span><b>{money(borrowedMV)}</b><small>{totalMV ? (borrowedMV / totalMV * 100).toFixed(1) : '0.0'}% of total</small></div><div><span>Total Qty</span><b>{fmt(totalQty)}</b></div><div><span>Owned Qty</span><b>{fmt(ownedQty)}</b></div><div><span>Borrowed Qty</span><b>{fmt(borrowedQty)}</b></div><div><span>Utilisation</span><b>{utilisation.toFixed(1)}%</b></div>
    </section>
    <nav className="holdings-tabs">{['All Holdings', 'Owned Securities', 'Borrowed Securities'].map(value => <button key={value} className={tab === value ? 'active' : ''} onClick={() => setTab(value)}>{value}</button>)}</nav>
    <div className="holdings-filterbar"><label className="holdings-search"><Search size={15}/><input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search by ISIN, symbol or security name"/></label><select value={assetFilter} onChange={e => setAssetFilter(e.target.value)}><option value="All">Asset Class: All</option>{assetOptions.map(value => <option key={value}>{value}</option>)}</select><select value={ownershipFilter} onChange={e => setOwnershipFilter(e.target.value)}><option value="All">Ownership: All</option><option>Owned</option><option>Borrowed</option></select><select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="All">Status: All</option>{[...new Set(rows.map(row => String(row.status)))].map(value => <option key={value}>{value}</option>)}</select><button className="holdings-filter-button" title="Clear filters" onClick={() => { setSearch(''); setAssetFilter('All'); setOwnershipFilter('All'); setStatusFilter('All'); setTab('All Holdings') }}><SlidersHorizontal size={15}/></button></div>
    <section className="holdings-table-section"><div className="holdings-table-scroll"><table className="holdings-table"><thead><tr><th>Symbol</th><th>Security Name</th><th>ISIN</th><th>Asset Class</th><th>Ownership</th><th className="num">Total Qty</th><th className="num">Available Qty</th><th className="num">Borrowed Qty</th><th className="num">Avg. Cost (₹)</th><th className="num">LTP (₹)</th><th className="num">Market Value (₹)</th><th className="num">Unrealized P&amp;L</th><th>Status</th></tr></thead><tbody>{filtered.map(row => <tr key={row.key} className={selected?.key === row.key ? 'selected' : ''} onClick={() => setSelectedKey(row.key)}><td><b>{row.symbol}</b></td><td>{row.securityName}</td><td>{row.isin || '—'}</td><td>{row.assetClass}</td><td><span className={'ownership-badge ' + row.ownership.toLowerCase()}>{row.ownership}</span></td><td className="num">{fmt(row.quantity)}</td><td className="num">{fmt(row.available)}</td><td className="num">{fmt(row.borrowedQty)}</td><td className="num">{money(row.averageCost)}</td><td className="num">{money(row.price)}</td><td className="num">{money(row.marketValue)}</td><td className={'num ' + (num(row.pnl) >= 0 ? 'positive' : 'negative')}>{row.pnl == null ? '—' : money(row.pnl)}</td><td><span className={'holding-status ' + statusTone(row.status)}>{String(row.status || '—').replaceAll('_', ' ')}</span></td></tr>)}</tbody></table>{loading && <div className="holdings-empty">Loading live holdings…</div>}{!loading && !filtered.length && <div className="holdings-empty">{error || 'No holdings match the selected filters.'}</div>}</div><footer className="holdings-table-footer"><span>Showing {filtered.length ? 1 : 0}–{filtered.length} of {rows.length} holdings</span><span>{error ? 'Live data unavailable' : 'Live data · refreshes every 30 seconds'}</span></footer></section>
    {selected && <section className="holdings-detail"><header><b>{selected.symbol} – {selected.securityName}</b><span className={'ownership-badge ' + selected.ownership.toLowerCase()}>{selected.ownership}</span><a href={'/trading'} title="Open Trading"><ExternalLink size={13}/></a></header><nav>{['Overview', 'Borrowing Details', 'Trading Activity', 'Price & Valuation'].map(value => <button key={value} className={detailTab === value ? 'active' : ''} onClick={() => setDetailTab(value)}>{value}</button>)}</nav>
      {detailTab === 'Overview' && <div className="holdings-detail-grid"><section><h3>Security Information</h3><DetailLine label="ISIN" value={selected.isin}/><DetailLine label="Exchange" value={pick(selected.security, 'exchange')}/><DetailLine label="Asset Class" value={selected.assetClass}/><DetailLine label="Sector" value={pick(selected.security, 'sector')}/></section><section><h3>Holding Summary</h3><DetailLine label="Ownership" value={selected.ownership}/><DetailLine label="Total Qty" value={fmt(selected.quantity)}/><DetailLine label="Available Qty" value={fmt(selected.available)}/><DetailLine label="Borrowed Qty" value={fmt(selected.borrowedQty)}/></section><section><h3>Valuation</h3><DetailLine label="Avg. Cost (₹)" value={money(selected.averageCost)}/><DetailLine label="LTP (₹)" value={money(selected.price)}/><DetailLine label="Market Value (₹)" value={money(selected.marketValue)}/><DetailLine label="Unrealized P&amp;L" value={selected.pnl == null ? '—' : money(selected.pnl)} emphasis={num(selected.pnl) >= 0}/></section><section><h3>Recent Activity</h3><div className="holdings-activity-head"><span>Date</span><span>Activity</span><span>Qty</span></div>{activity.length ? activity.slice(0, 4).map((event, i) => <div className="holdings-activity-row" key={i}><span>{date(event.date)}</span><b>{event.kind}</b><span>{fmt(event.qty)}</span></div>) : <div className="holdings-empty small">No recent activity</div>}</section></div>}
      {detailTab === 'Borrowing Details' && <div className="holdings-detail-grid"><section><h3>Borrowing Position</h3><DetailLine label="Borrowed Quantity" value={fmt(selected.ownership === 'Borrowed' ? selected.quantity : selected.borrowedQty)}/><DetailLine label="Lender" value={selected.lender}/><DetailLine label="Loan Status" value={selected.status}/><DetailLine label="Maturity" value={date(selected.maturity)}/></section><section><h3>Loan Records</h3>{loans.length ? loans.slice(0, 5).map((loan, i) => <DetailLine key={i} label={'Loan #' + pick(loan, 'id')} value={pick(loan, 'status')}/> ) : <DetailLine label="Loan records" value="No linked loans returned by API"/>}</section></div>}
      {detailTab === 'Trading Activity' && <div className="holdings-detail-grid"><section className="wide"><h3>Recent Trades</h3><div className="holdings-activity-head"><span>Date</span><span>Side</span><span>Quantity / Price</span></div>{trades.length ? trades.slice(0, 8).map((trade, i) => <div className="holdings-activity-row" key={i}><span>{date(pick(trade, 'tradedAt', 'traded_at', 'createdAt', 'created_at'))}</span><b>{String(pick(trade, 'side') || 'Trade').toUpperCase()}</b><span>{fmt(pick(trade, 'quantity'))} / {money(pick(trade, 'price'))}</span></div>) : <div className="holdings-empty small">No trade records for this security</div>}</section></div>}
      {detailTab === 'Price & Valuation' && <div className="holdings-detail-grid"><section><h3>Market Valuation</h3><DetailLine label="Last Traded Price" value={money(selected.price)}/><DetailLine label="Average Cost" value={money(selected.averageCost)}/><DetailLine label="Market Value" value={money(selected.marketValue)}/><DetailLine label="Unrealized P&amp;L" value={selected.pnl == null ? '—' : money(selected.pnl)}/></section><section><h3>Position Data</h3><DetailLine label="Total Quantity" value={fmt(selected.quantity)}/><DetailLine label="Available Quantity" value={fmt(selected.available)}/><DetailLine label="Ownership" value={selected.ownership}/></section></div>}
    </section>}
  </main>
}
