import SessionBar from './SessionBar'
import './BoundedWorkspace.css'
import { Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import DeliveryFailures from './DeliveryFailures'
import { lenderGet, lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'

export default function PortalLayout() {
  const { pathname } = useLocation()
  const isOverview = pathname === '/'
  const bounded = pathname === '/' || pathname === '/fees-rebates' || pathname === '/audit-notifications'
  const [searchTerm, setSearchTerm] = useState('')
  const [compactSidebar, setCompactSidebar] = useState(() => localStorage.getItem('lenddesk-sidebar') === 'compact')
  const { mode, organizationId, refresh } = useDataSource()

  useEffect(() => {
    localStorage.setItem('lenddesk-sidebar', compactSidebar ? 'compact' : 'expanded')
  }, [compactSidebar])

  return <main className={`app-shell ${compactSidebar ? 'sidebar-compact' : ''}`}>
    <Sidebar compact={compactSidebar} onToggleCompact={() => setCompactSidebar(value => !value)} />
    <section className={`workspace ${bounded ? 'bounded-workspace' : ''}`}>
      <SessionBar />
      <DeliveryFailures key={organizationId} enabled={mode === 'live'} organizationId={organizationId} get={lenderGet} post={lenderPost} onChanged={refresh} />
      {isOverview && <Topbar onSearch={setSearchTerm} />}
      {bounded ? <div className={`bounded-route ${pathname === '/' ? 'overview-workspace' : pathname === '/audit-notifications' ? 'audit-workspace' : 'fees-workspace'}`}><Outlet context={{ searchTerm: isOverview ? searchTerm : '', dataMode: mode }} /></div> : <Outlet context={{ searchTerm: isOverview ? searchTerm : '', dataMode: mode }} />}
    </section>
  </main>
}
