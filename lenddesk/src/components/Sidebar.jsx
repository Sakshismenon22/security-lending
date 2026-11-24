import { ChevronLeft, ChevronRight } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { navigation } from '../data/navigation'

export default function Sidebar({ compact, onToggleCompact }) {
  return <aside className={`sidebar ${compact ? 'is-compact' : ''}`}>
    <div className="brand">
      <span className="brand-mark" aria-hidden="true">L</span>
      <span className="brand-name">LendDesk</span>
      <button
        className="sidebar-toggle"
        type="button"
        onClick={onToggleCompact}
        aria-label={compact ? 'Expand navigation' : 'Collapse navigation'}
        title={compact ? 'Expand navigation' : 'Collapse navigation'}
      >
        {compact ? <ChevronRight size={16} aria-hidden="true" /> : <ChevronLeft size={16} aria-hidden="true" />}
      </button>
    </div>
    <nav aria-label="Lender portal navigation">
      {navigation.map(([Icon, label, path]) => <NavLink key={path} to={path} end={path === '/'} aria-label={label} title={compact ? label : undefined}>
        <Icon className="sidebar-nav-icon" size={16} aria-hidden="true" focusable="false" />
        <span className="nav-label">{label}</span>
      </NavLink>)}
    </nav>
    <div className="sidebar-footer"><i /><span>All services normal</span><b>12 ms</b></div>
  </aside>
}
