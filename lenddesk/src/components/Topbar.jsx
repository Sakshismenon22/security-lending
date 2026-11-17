import { useEffect, useState } from 'react'
import { useDataSource } from '../hooks/useDataSource'

export default function Topbar({ onSearch }) {
  const [value, setValue] = useState('')
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(timer)
  }, [])
  const { identity, mode, setMode, organizationId, setOrganizationId, refresh } = useDataSource()
  const updateSearch = (nextValue) => { setValue(nextValue); onSearch(nextValue) }
  return <header className="topbar">
    <span className="org">{identity?.organization.name ?? "Apex Prime Fund"}</span>
    <label className="search">⌕<input value={value} onChange={(event) => updateSearch(event.target.value)} placeholder="Search loans, securities, borrowers, or IDs…" /></label>
    {!identity && <label className="data-source"><span>Data</span><select value={mode} onChange={(event) => setMode(event.target.value)} aria-label="Data source"><option value="demo">Demo</option><option value="live">Live</option></select></label>}
    {!identity && mode === 'live' && <label className="org-id">Org <input value={organizationId} onChange={(event) => setOrganizationId(event.target.value.replace(/\D/g, '') || '1')} aria-label="Lender organization ID" /></label>}
    <strong>{now.toLocaleTimeString('en-IN', { timeZone: 'Asia/Kolkata', hour12: false })} IST</strong><button className="plain" aria-label="Refresh live data" onClick={refresh}>↻</button>
    <button className="plain" aria-label="Notifications">♧</button><button className="avatar">JD</button>
  </header>
}
