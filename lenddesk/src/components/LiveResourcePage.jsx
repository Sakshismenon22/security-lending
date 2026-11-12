import { useMemo } from 'react'
import { useLenderData } from '../hooks/useLenderData'

const pretty = (value) => {
  if (value == null) return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export default function LiveResourcePage({ title, resource }) {
  const { data, loading, error } = useLenderData(resource)
  const rows = useMemo(() => Array.isArray(data) ? data : data ? [data] : [], [data])
  const columns = useMemo(() => [...new Set(rows.flatMap((row) => Object.keys(row)))], [rows])
  return <section className="live-resource-page"><div className="live-resource-heading"><div><small>LIVE LENDER READ MODEL</small><h1>{title}</h1><p>Organization-scoped data from the lender-service API. Refresh using the button in the top bar.</p></div><span>{loading ? 'Loading…' : error ? 'Connection error' : `${rows.length} records`}</span></div>{error ? <div className="live-error"><b>Live API unavailable.</b> {error}<span>Confirm lender-service is running on port 8084 and the organization ID is correct.</span></div> : <article className="panel"><div className="panel-title"><h2>{title} data</h2><small>{loading ? 'Fetching latest projection…' : 'Live read-only projection'}</small></div><div className="scroll"><table><thead><tr>{columns.map((column) => <th key={column}>{column}</th>)}</tr></thead><tbody>{rows.map((row, index) => <tr key={row.id ?? index}>{columns.map((column) => <td key={column}>{pretty(row[column])}</td>)}</tr>)}</tbody></table></div>{!loading && !rows.length && <div className="live-empty">No records were returned for this organization.</div>}</article>}</section>
}
