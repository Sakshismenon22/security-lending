import { useState } from 'react'

export default function Topbar({ onSearch }) {
  const [value, setValue] = useState('')
  const updateSearch = (nextValue) => { setValue(nextValue); onSearch(nextValue) }
  return <header className="topbar">
    <button className="org">Apex Prime Fund <b>⌄</b></button>
    <label className="search">⌕<input value={value} onChange={(event) => updateSearch(event.target.value)} placeholder="Search loans, securities, borrowers, or IDs…" /></label>
    <strong>UTC 14:27:31</strong><button className="plain" aria-label="Refresh">↻</button>
    <button className="plain" aria-label="Notifications">♧</button><button className="avatar">JD</button>
  </header>
}
