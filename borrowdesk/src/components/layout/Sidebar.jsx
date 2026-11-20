import { useEffect, useState } from 'react'
import { getWorkspaceIdentity } from '../../api/sessionApi'
import { request, orgUrl } from '../../api/apiClient'
import { NavLink } from 'react-router-dom'
import { ArrowLeftRight, CalendarClock, ChartNoAxesCombined, ChevronLeft, ChevronRight, ClipboardList, Coins, FileCheck2, FileText, Layers3, Search, ShieldAlert, ShieldCheck, Wallet, BriefcaseBusiness } from 'lucide-react'

const groups = [
  { title: 'WORKSPACE', items: [['/blotter', 'Overview', ClipboardList], ['/trading', 'Trading', ChartNoAxesCombined], ['/locates', 'Locate requests', Search], ['/reservations', 'Reservations', CalendarClock], ['/holdings', 'Holdings', BriefcaseBusiness], ['/loans', 'Active loans', Layers3]] },
  { title: 'TREASURY', items: [['/collateral', 'Collateral', ShieldCheck], ['/margin-calls', 'Margin calls', ShieldAlert], ['/funding', 'Funding & account', Wallet], ['/fees-rebates', 'Fees & rebates', Coins]] },
  { title: 'OPERATIONS', items: [['/returns-recalls', 'Returns & recalls', ArrowLeftRight], ['/agreements', 'Agreements', FileCheck2], ['/reports-audit', 'Reports & audit', FileText]] },
]

const roleNames = { ADMIN: 'Administrator', OPERATIONS: 'Operations', TRADER: 'Trader', READ_ONLY: 'Read only', UNVERIFIED: 'Role unverified', INACTIVE_OR_UNREGISTERED: 'No active workspace role', UNKNOWN: 'Role unavailable' }

export default function Sidebar({ collapsed, onToggle }) {
  const identity = getWorkspaceIdentity()
  const organizationId = identity?.organization?.platformOrganizationId
  const name = identity?.user?.fullName?.trim() || identity?.user?.email || 'Not signed in'
  const parts = name.split(/\s+/u)
  const initials = identity ? [parts[0], ...(parts.length > 1 ? [parts.at(-1)] : [])].map(part => Array.from(part)[0]).join('').toUpperCase() : '—'
  const [role, setRole] = useState(null)
  useEffect(() => {
    if (!organizationId) return
    const controller = new AbortController()
    let inFlight = false
    async function loadRole() {
      if (inFlight) return
      inFlight = true
      try {
        const member = await request(orgUrl('/admin-access'), { signal: controller.signal })
        if (!controller.signal.aborted) setRole(typeof member?.role === 'string' ? member.role : 'UNKNOWN')
      } catch {
        if (!controller.signal.aborted) setRole('UNKNOWN')
      } finally { inFlight = false }
    }
    void loadRole()
    window.addEventListener('focus', loadRole)
    return () => { controller.abort(); window.removeEventListener('focus', loadRole) }
  }, [organizationId])
  const roleLabel = !identity ? 'Not signed in' : role == null ? 'Checking role…' : roleNames[role] || role.toLowerCase().replaceAll('_', ' ').replace(/^./, letter => letter.toUpperCase())

  return <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
    <div className="brand"><b>B</b><span>BorrowDesk</span><button className="sidebar-toggle" type="button" onClick={onToggle} title={collapsed ? 'Expand navigation' : 'Collapse navigation'} aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}>{collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}</button></div>
    <nav className="nav-scroll">{groups.map(group => <section key={group.title}><small>{group.title}</small>{group.items.map(([to, label, Icon]) => <NavLink key={to} to={to} title={collapsed ? label : undefined} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}><Icon size={16} /><span>{label}</span></NavLink>)}</section>)}</nav>
    <div className="profile" role="group" aria-label="Signed-in profile" title={`${name} · ${roleLabel}`}><b aria-hidden="true">{initials}</b><span><span className="profile-name">{name}</span><small>{roleLabel}</small></span></div>
  </aside>
}
