import './BoundedWorkspace.css'
import { Outlet, useLocation } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import { useDataSource } from '../hooks/useDataSource'

export default function PortalLayout() {
  const { pathname } = useLocation()
  const bounded = pathname === '/' || pathname === '/fees-rebates' || pathname === '/audit-notifications'
  const [searchTerm, setSearchTerm] = useState('')
  const [compactSidebar, setCompactSidebar] = useState(() => localStorage.getItem('lenddesk-sidebar') === 'compact')
  const { mode } = useDataSource()

  useEffect(() => {
    localStorage.setItem('lenddesk-sidebar', compactSidebar ? 'compact' : 'expanded')
  }, [compactSidebar])

  return <main className={`app-shell ${compactSidebar ? 'sidebar-compact' : ''}`}>
    <Sidebar compact={compactSidebar} onToggleCompact={() => setCompactSidebar(value => !value)} />
    <section className={`workspace ${bounded ? 'bounded-workspace' : ''}`}>
      <Topbar onSearch={setSearchTerm} />
      {bounded ? <div className={`bounded-route ${pathname === '/' ? 'overview-workspace' : pathname === '/audit-notifications' ? 'audit-workspace' : 'fees-workspace'}`}><Outlet context={{ searchTerm, dataMode: mode }} /></div> : <Outlet context={{ searchTerm, dataMode: mode }} />}
    </section>
  </main>
}
