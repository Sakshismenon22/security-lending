import { Fragment, useMemo, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import StatusBadge from './StatusBadge'

const statusTone = (value) => {
  if (/overdue|exception|deficit|margin call|required|failed|high/i.test(value)) return 'risk'
  if (/open|pending|awaiting|scheduled|acknowledged|accrued/i.test(value)) return 'warn'
  if (/completed|closed|posted|settled|processed|accepted|active|online|healthy|read/i.test(value)) return 'good'
  return 'neutral'
}

export default function OperationalRegisterPage({ config }) {
  const { searchTerm } = useOutletContext()
  const [tab, setTab] = useState(config.tabs[0][0])
  const [selectedId, setSelectedId] = useState(config.rows[0].id)
  const [expandedId, setExpandedId] = useState(config.rows[0].id)
  const rows = useMemo(() => config.rows.filter((row) => {
    const label = tab.toLowerCase()
    const status = row.status.toLowerCase()
    const type = (row.type ?? '').toLowerCase()
    const matchesTab = label.includes('all')
      || (label.includes('open') && /open|awaiting|accepted|active|pending|acknowledged|return scheduled/.test(status))
      || (label.includes('overdue') && /overdue/.test(status))
      || (label.includes('closed') && /closed/.test(status))
      || (label.includes('completed') && /completed/.test(status))
      || (label.includes('due today') && /open|awaiting|accepted|pending/.test(status))
      || (label.includes('fees') && /fee/.test(type))
      || (label.includes('rebates') && /rebate/.test(type))
      || (label.includes('due settlement') && /pending|scheduled/.test(status))
      || (label.includes('cash') && /fee|collateral/.test(type))
      || (label.includes('securities') && /security/.test(type))
      || (label.includes('inbound') && row.type === 'IN')
      || (label.includes('outbound') && row.type === 'OUT')
      || (label.includes('exceptions') && /exception/.test(status))
      || (label.includes('notifications') && /unread|read|acknowledged/.test(status))
      || (label.includes('user actions') && /user action/.test(type))
      || (label.includes('system events') && /system event/.test(type))
      || (label.includes('users') && /administrator/.test(type))
      || (label.includes('organizations') && /organization/.test(type))
      || (label.includes('service connectivity') && /service|session/.test(type))
      || (label.includes('configurations') && /configuration/.test(type))
    return matchesTab && Object.values(row).join(' ').toLowerCase().includes(searchTerm.toLowerCase())
  }), [config.rows, searchTerm, tab])
  function selectRow(id) { setSelectedId(id); setExpandedId((current) => current === id ? null : id) }
  return <>
    <section className="heading generic-heading"><h1>{config.title}</h1><div>{config.metrics.map(([label, value], index) => <span key={label} className={index === config.metrics.length - 1 && /overdue|exception|deficit/i.test(label) ? 'critical' : ''}>{label} <b>{value}</b></span>)}</div><button className="primary-action" type="button">{config.action}</button></section>
    <section className="generic-toolbar panel"><div className="table-tabs" role="tablist">{config.tabs.map(([label, count]) => <button key={label} type="button" className={tab === label ? 'active' : ''} onClick={() => setTab(label)}>{label} <StatusBadge tone="neutral">{count}</StatusBadge></button>)}</div><div><button className="outline-action" type="button">Columns</button><button className="outline-action" type="button">Export</button></div></section>
    <section className="generic-workspace"><article className="panel generic-register"><div className="panel-title"><h2>{config.title} register <small>{rows.length} records shown</small></h2><small>Expand a record to review its related operational detail</small></div><div className="scroll"><table><thead><tr><th />{config.columns.map(([label]) => <th key={label}>{label}</th>)}</tr></thead><tbody>{rows.map((row) => <Fragment key={row.id}><tr className={selectedId === row.id ? 'selected' : ''} onClick={() => selectRow(row.id)}><td><button className="row-expander" type="button" aria-label={`${expandedId === row.id ? 'Collapse' : 'Expand'} ${row.id}`}>{expandedId === row.id ? '⌄' : '›'}</button></td>{config.columns.map(([label, key]) => <td key={key} className={/amount|quantity/i.test(label) ? 'num' : ''}>{key === 'status' ? <StatusBadge tone={statusTone(row[key])}>{row[key]}</StatusBadge> : key === 'id' || key === 'loan' || key === 'returnId' ? <a>{row[key]}</a> : row[key]}</td>)}</tr>{expandedId === row.id && <tr className="child-row"><td colSpan={config.columns.length + 1}><div className="child-table generic-child"><div><b>Record detail · {row.id}</b><span>{config.title} operational context</span></div><table><thead><tr><th>Field</th><th>Value</th></tr></thead><tbody>{row.detail.map((item) => <tr key={item[0]}><td>{item[0]}</td><td>{item[1]}</td></tr>)}</tbody></table><div className="generic-actions"><span>Reference <b>{row.id}</b> · State <b>{row.status}</b></span><div><button className="outline-action" type="button">View related record</button><button className="primary-action" type="button">Open workflow</button></div></div></div></td></tr>}</Fragment>)}</tbody></table></div><footer>Showing {rows.length} records · {config.footer}</footer></article></section>
  </>
}
