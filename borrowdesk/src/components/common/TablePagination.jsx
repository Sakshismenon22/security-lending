import './TablePagination.css';
export default function TablePagination({model,label}) {
  return <nav className="table-pagination" aria-label={`${label} pagination`}>
    <span>{model.start}–{model.end} of {model.total}</span>
    <label>Rows <select aria-label={`${label} rows per page`} value={model.size} onChange={e=>model.setSize(e.target.value)}>{[10,25,50].map(n=><option key={n}>{n}</option>)}</select></label>
    <button disabled={model.page===0} onClick={()=>model.setPage(model.page-1)}>Previous</button>
    <span>Page {model.page+1} of {model.pages}</span>
    <button disabled={model.page+1>=model.pages} onClick={()=>model.setPage(model.page+1)}>Next</button>
  </nav>;
}
