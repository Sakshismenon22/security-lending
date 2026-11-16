import { Children, cloneElement, useState, useRef, useEffect } from 'react'
import './PaginatedTable.css'

// Keep expanded rows together: each keyed Fragment is one record.
export default function PaginatedTable({ children: table, className = '', label = 'Records' }) {
  const body = Children.toArray(table.props.children).find(child => child.type === 'tbody')
  const entries = Children.toArray(body?.props.children)
  const empty = entries.length === 1 && Children.toArray(entries[0].props.children).some(cell => cell.props?.colSpan > 1)
  const rows = empty ? [] : entries
  const signature = rows.map(row => row.key).join('|')
  const [state, setState] = useState({ signature, page: 0, size: 10 })
  const pages = Math.max(1, Math.ceil(rows.length / state.size))
  const page = state.signature === signature ? Math.min(state.page, pages - 1) : 0
  const scroll = useRef(null)
  useEffect(() => { if (scroll.current) scroll.current.scrollTop = 0 }, [page, state.size, signature])
  const content = cloneElement(table, {}, Children.map(table.props.children, child => child?.type === 'tbody'
    ? cloneElement(child, {}, empty ? entries : rows.slice(page * state.size, (page + 1) * state.size)) : child))
  const update = changes => setState({ ...state, signature, page, ...changes })
  return <div className={`paginated-table ${className}`}>
    <div className="paginated-table-scroll" ref={scroll}>{content}</div>
    <div className="table-pagination" role="navigation" aria-label={`${label} pagination`}>
      <span>{rows.length ? page * state.size + 1 : 0}–{Math.min((page + 1) * state.size, rows.length)} of {rows.length}</span>
      <label>Rows <select aria-label={`${label} rows per page`} value={state.size} onChange={e => update({size:Number(e.target.value),page:0})}>{[10,25,50].map(n => <option key={n}>{n}</option>)}</select></label>
      <button disabled={!page} onClick={() => update({page:page-1})}>Previous</button>
      <span>Page {page+1} of {pages}</span>
      <button disabled={page+1>=pages} onClick={() => update({page:page+1})}>Next</button>
    </div>
  </div>
}
