import { NavLink } from 'react-router-dom'
import { navigation } from '../data/navigation'

export default function Sidebar({ compact, onToggleCompact }) {
  return <aside className={`sidebar ${compact ? 'is-compact' : ''}`}>
    <div className="brand">
      <span className="brand-mark">⌁</span>
      <span className="brand-name">LendDesk</span>
      <button
        className="sidebar-toggle"
        type="button"
        onClick={onToggleCompact}
        aria-label={compact ? 'Expand navigation' : 'Collapse navigation'}
        title={compact ? 'Expand navigation' : 'Collapse navigation'}
      >
        {compact ? '›' : '‹'}
      </button>
    </div>
    <nav aria-label="Lender portal navigation">
      {navigation.map(([icon, label, path]) => <NavLink key={label} to={path} end={path === '/'}>
        <span aria-hidden="true">{icon}</span><span className="nav-label">{label}</span>
      </NavLink>)}
    </nav>
    <div className="sidebar-footer"><i /><span>All services normal</span><b>12 ms</b></div>
  </aside>
}
