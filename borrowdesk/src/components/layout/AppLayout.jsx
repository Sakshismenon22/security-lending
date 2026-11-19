import SessionBar from '../SessionBar'
import { useState } from 'react'
import { Outlet, useLocation } from 'react-router-dom'
import Sidebar from './Sidebar'
import Topbar from './Topbar'
import DeliveryFailures from '../DeliveryFailures'
import { get, post, ORG_ID } from '../../api/apiClient'

export default function AppLayout() {
  const [navCollapsed, setNavCollapsed] = useState(false)
  const { pathname } = useLocation()
  const showTopbar = pathname === '/blotter'

  return <div className={`app-shell${navCollapsed ? ' nav-collapsed' : ''}`}>
    <Sidebar collapsed={navCollapsed} onToggle={() => setNavCollapsed(value => !value)} />
    <main className="main-shell">
      <SessionBar />
      {showTopbar && <Topbar />}
      <DeliveryFailures organizationId={ORG_ID} get={get} post={post} />
      <div className="route-outlet"><Outlet /></div>
    </main>
  </div>
}
