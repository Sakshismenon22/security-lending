import { Outlet } from 'react-router-dom'
import { useEffect, useState } from 'react'
import Sidebar from './Sidebar'
import Topbar from './Topbar'

export default function PortalLayout() {
  const [searchTerm, setSearchTerm] = useState('')
  const [compactSidebar, setCompactSidebar] = useState(() => localStorage.getItem('lenddesk-sidebar') === 'compact')

  useEffect(() => {
    localStorage.setItem('lenddesk-sidebar', compactSidebar ? 'compact' : 'expanded')
  }, [compactSidebar])

  return <main className={`app-shell ${compactSidebar ? 'sidebar-compact' : ''}`}>
    <Sidebar compact={compactSidebar} onToggleCompact={() => setCompactSidebar(value => !value)} />
    <section className="workspace"><Topbar onSearch={setSearchTerm} /><Outlet context={{ searchTerm }} /></section>
  </main>
}
