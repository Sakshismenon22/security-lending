import { Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import { useDataSource } from '../hooks/useDataSource'
import LiveResourcePage from './LiveResourcePage'

const liveResource = (pathname) => {
  const segment = pathname.split('/').filter(Boolean)[0] ?? ''
  return ({
    '': ['Operations Overview', 'dashboard'], agreements: ['Master Securities Loan Agreements', 'agreements'], borrowers: ['Borrowers & Counterparties', 'borrowers'], inventory: ['Lendable Inventory', 'inventory'], locates: ['Locate Requests', 'locates'], reservations: ['Inventory Reservations', 'reservations'], loans: ['Active Loans', 'loans'], collateral: ['Collateral Positions', 'collateral'], returns: ['Return Settlements', 'returns'], valuations: ['Daily Valuations', 'valuations'], 'margin-calls': ['Margin Calls', 'margin-calls'], recalls: ['Recalls', 'recalls'], 'fees-rebates': ['Fees & Rebates', 'accruals'], ledger: ['Settlement Ledger', 'ledger'], 'fix-monitor': ['FIX Monitor / Audit Events', 'audit'], 'audit-notifications': ['Audit & Notifications', 'audit'], administration: ['Administration', 'users'],
  })[segment] ?? null
}

export default function PortalLayout() {
  const [searchTerm, setSearchTerm] = useState('')
  const [compactSidebar, setCompactSidebar] = useState(() => localStorage.getItem('lenddesk-sidebar') === 'compact')
  const { mode, organizationId } = useDataSource()
  const { pathname } = useLocation()
  const resource = liveResource(pathname)

  useEffect(() => {
    localStorage.setItem('lenddesk-sidebar', compactSidebar ? 'compact' : 'expanded')
  }, [compactSidebar])

  return <main className={`app-shell ${compactSidebar ? 'sidebar-compact' : ''}`}>
    <Sidebar compact={compactSidebar} onToggleCompact={() => setCompactSidebar(value => !value)} />
    <section className="workspace"><Topbar onSearch={setSearchTerm} />{mode === 'live' && <div className="live-banner">LIVE API · Lender service · Organization {organizationId}</div>}{mode === 'live' && resource && pathname.split('/').filter(Boolean)[0] !== 'margin-calls' ? <LiveResourcePage title={resource[0]} resource={resource[1]} /> : <Outlet context={{ searchTerm, dataMode: mode }} />}</section>
  </main>
}
