import { NavLink } from 'react-router-dom'
import { ArrowLeftRight, CalendarClock, ChartNoAxesCombined, ChevronLeft, ChevronRight, ClipboardList, Coins, FileCheck2, FileText, Layers3, Search, ShieldAlert, ShieldCheck, Wallet } from 'lucide-react'

const groups = [
  { title: 'WORKSPACE', items: [['/blotter', 'Borrowing blotter', ClipboardList], ['/trading', 'Trading', ChartNoAxesCombined], ['/locates', 'Locate requests', Search], ['/reservations', 'Reservations', CalendarClock], ['/loans', 'Active loans', Layers3]] },
  { title: 'TREASURY', items: [['/collateral', 'Collateral', ShieldCheck], ['/margin-calls', 'Margin calls', ShieldAlert], ['/funding', 'Funding & account', Wallet], ['/fees-rebates', 'Fees & rebates', Coins]] },
  { title: 'OPERATIONS', items: [['/returns-recalls', 'Returns & recalls', ArrowLeftRight], ['/agreements', 'Agreements', FileCheck2], ['/reports-audit', 'Reports & audit', FileText]] },
]

export default function Sidebar({ collapsed, onToggle }) {
  return <aside className={`sidebar${collapsed ? ' collapsed' : ''}`}>
    <div className="brand"><b>B</b><span>BorrowDesk</span><button className="sidebar-toggle" type="button" onClick={onToggle} title={collapsed ? 'Expand navigation' : 'Collapse navigation'} aria-label={collapsed ? 'Expand navigation' : 'Collapse navigation'}>{collapsed ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}</button></div>
    <nav className="nav-scroll">{groups.map(group => <section key={group.title}><small>{group.title}</small>{group.items.map(([to, label, Icon]) => <NavLink key={to} to={to} title={collapsed ? label : undefined} className={({ isActive }) => isActive ? 'nav-link active' : 'nav-link'}><Icon size={16} /><span>{label}</span></NavLink>)}</section>)}</nav>
    <div className="profile"><b>NV</b><span>Niranjan V.<small>Operations</small></span></div>
  </aside>
}
