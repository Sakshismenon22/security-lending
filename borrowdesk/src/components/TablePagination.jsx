import React from 'react';
import './TablePagination.css';

export default function TablePagination({page,pageSize,total,onPageChange,onPageSizeChange}) {
  const pages=Math.max(1,Math.ceil(total/pageSize));
  const start=total===0?0:(page-1)*pageSize+1;
  const end=Math.min(page*pageSize,total);
  return <footer className="shared-table-pagination">
    <span>Showing {start}–{end} of {total} records</span>
    <div className="shared-table-pagination-controls">
      <label>Rows per page <select value={pageSize} onChange={e=>onPageSizeChange(Number(e.target.value))}><option value={25}>25</option><option value={50}>50</option><option value={100}>100</option></select></label>
      <button type="button" disabled={page<=1} onClick={()=>onPageChange(Math.max(1,page-1))}>Previous</button>
      <span>Page {page} of {pages}</span>
      <button type="button" disabled={page>=pages} onClick={()=>onPageChange(Math.min(pages,page+1))}>Next</button>
    </div>
  </footer>;
}
