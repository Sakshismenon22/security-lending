import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function AppLayout() {
  const [navCollapsed, setNavCollapsed] = useState(false)
  const { pathname } = useLocation()
  const showTopbar = pathname === '/blotter'

  return <div className={`app-shell${navCollapsed ? ' nav-collapsed' : ''}`}>
    <Sidebar collapsed={navCollapsed} onToggle={() => setNavCollapsed(value => !value)} />
    <main className="main-shell">
      {showTopbar && <Topbar />}
      <div className="route-outlet"><Outlet /></div>
    </main>
  </div>
}
