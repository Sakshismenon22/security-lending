import { useState } from 'react'
import { Outlet } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import FooterStatus from './FooterStatus'

export default function AppLayout() {
  const [navCollapsed, setNavCollapsed] = useState(false)
  return <div className={`app-shell${navCollapsed ? ' nav-collapsed' : ''}`}>
    <Sidebar collapsed={navCollapsed} onToggle={() => setNavCollapsed(value => !value)} />
    <main className="main-shell"><Topbar /><div className="route-outlet"><Outlet /></div><FooterStatus /></main>
  </div>
}
