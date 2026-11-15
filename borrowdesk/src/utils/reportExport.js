export function exportCsv(name, columns, records) {
  const quote = value => {
    let text = String(value ?? '')
    if (/^[\s]*[=+@-]/.test(text)) text = "'" + text
    return '"' + text.replaceAll('"', '""') + '"'
  }
  const csv = [columns, ...records.map(row => columns.map(column => row[column]))].map(row => row.map(quote).join(',')).join('\r\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name.toLowerCase().replace(/[^a-z0-9]+/g, '-') + '.csv'
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
  return new Blob([csv]).size
}

export function exportPdf(name, columns, records) {
  const popup = window.open('', '_blank')
  if (!popup) throw new Error('Allow popups to open the printable report.')
  const escape = value => String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char])
  popup.document.write(`<!doctype html><html><head><title>${escape(name)}</title><style>@page{size:A4 landscape;margin:12mm}body{font:11px Arial;color:#202833}table{width:100%;border-collapse:collapse}th,td{border:1px solid #ddd;padding:6px;text-align:left;overflow-wrap:anywhere}th{background:#eee}thead{display:table-header-group}tr{break-inside:avoid}</style></head><body><h1>${escape(name)}</h1><p>${records.length} records · Generated ${escape(new Date().toLocaleString())}</p><table><thead><tr>${columns.map(column => `<th>${escape(column)}</th>`).join('')}</tr></thead><tbody>${records.map(row => `<tr>${columns.map(column => `<td>${escape(row[column])}</td>`).join('')}</tr>`).join('')}</tbody></table></body></html>`)
  popup.document.close()
  popup.focus()
  setTimeout(() => popup.print(), 250)
}
