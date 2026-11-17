import { useMemo, useState } from 'react'
import { Link, useOutletContext, useSearchParams } from 'react-router-dom'
import ReferenceTable from '../components/reference/ReferenceTable'
import ReferenceRegister from '../components/reference/ReferenceRegister'
import { FinanceState, ReferenceFilters, ReferenceMetrics } from '../components/reference/ReferenceControls'
import { useLenderData } from '../hooks/useLenderData'
import './ReferencePages.css'
import './FinanceReferencePages.css'

const money = (value) => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(Number(value ?? 0))
const label = (value) => String(value ?? '—').toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase())
const loanLabel = id => id == null ? '—' : `LOAN-${String(id).padStart(6, '0')}`
const ledgerId = (id) => `LED-${String(id).padStart(6, '0')}`
const timestamp = (value) => value ? new Intl.DateTimeFormat('en-GB', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'medium' }).format(new Date(value)).replace(',', '') : '—'
const columns = [['timestamp', 'IST Timestamp'], ['id', 'Ledger ID'], ['account', 'Account'], ['type', 'Entry Type'], ['debit', 'Debit'], ['credit', 'Credit'], ['currency', 'Currency'], ['borrower', 'Borrower'], ['loan', 'Loan ID'], ['reference', 'Reference Type / ID'], ['payment', 'Payment Request'], ['settlement', 'Settlement State'], ['status', 'Status']]
const blank = { account: '', type: '', borrower: '', loan: '', settlement: '' }

function accountFor(type) {
  if (/rebate/i.test(type)) return 'Cash collateral / rebate payable'
  if (/fee/i.test(type)) return 'Lender receivables / cash account'
  if (/buy.?in|default/i.test(type)) return 'Securities buy-in clearing'
  return 'Lender lifecycle settlement clearing'
}

function JournalDetails({ row }) {
  const [tab, setTab] = useState('Journal lines')
  const journal = { title: '▱ Journal Lines (2)', headers: ['Line#', 'Account', 'Description', 'Debit', 'Credit', 'Currency'], rows: [[1, row.account, `Record ${row.type.toLowerCase()} from ${row.reference}`, money(row.amount), '—', row.currency], [2, 'Lender settlement control account', `Balance ${row.type.toLowerCase()} entry`, '—', money(row.amount), row.currency], ['Total', '(balanced)', '', money(row.amount), money(row.amount), row.currency]] }
  const lifecycle = { title: '♧ Linked Lifecycle Record', headers: ['Type', 'Record ID', 'Description', 'Amount', 'Currency', 'State'], rows: [[row.type, row.reference, 'Source lifecycle event recorded by the lender service', money(row.amount), row.currency, <FinanceState value={row.settlement} />]] }
  const table = tab === 'Journal lines' ? journal : lifecycle
  return <div className="finance-details ledger-journal"><header><h2>▤ Journal Entry {row.id}</h2><FinanceState value="BALANCED" /><span>(2 lines, Δ ₹0.00)</span><small>Borrower: {row.borrower} | Loan: {row.loan} | Account: {row.account} | Entry Type: {row.type} | Posted: {row.timestamp} IST</small></header><div className="live-ledger-tabs" role="tablist"><button type="button" className={tab === 'Journal lines' ? 'active' : ''} aria-selected={tab === 'Journal lines'} onClick={() => setTab('Journal lines')}>Journal lines (2)</button><button type="button" className={tab === 'Lifecycle record' ? 'active' : ''} aria-selected={tab === 'Lifecycle record'} onClick={() => setTab('Lifecycle record')}>Linked lifecycle record</button></div><div className="live-ledger-detail-table"><ReferenceTable title={table.title} headers={table.headers} rows={table.rows} /></div><ReferenceTable title="◎ Audit Trail" headers={['IST Timestamp', 'Event', 'Actor', 'Details']} rows={[[row.timestamp, <FinanceState value="POSTED" />, 'lifecycle-service', `Ledger entry ${row.id} posted from source reference ${row.reference}.`]]} /></div>
}

export default function LiveLedgerPage() {
  const { searchTerm = '' } = useOutletContext()
  const { data, loading, error } = useLenderData('ledger')
  const [params, setParams] = useSearchParams()
  const reference = params.get('reference') ?? ''
  const [expanded, setExpanded] = useState(null)
  const [draft, setDraft] = useState(blank)
  const [filters, setFilters] = useState(blank)
  const entries = useMemo(() => (data ?? []).map((entry) => {
    const amount = Number(entry.amount ?? 0)
    const type = label(entry.type)
    return { id: ledgerId(entry.id), rawId: entry.id, timestamp: timestamp(entry.createdAt), account: accountFor(type), type, debit: amount, credit: amount, amount, currency: 'INR', borrower: entry.borrowerName ?? '—', connectionId: entry.connectionId, loan: loanLabel(entry.loanId), loanId: entry.loanId, reference: entry.reference ?? '—', payment: '—', settlement: 'SETTLED', status: 'POSTED' }
  }), [data])
  const rows = useMemo(() => entries.filter((row) => Object.entries(filters).every(([key, value]) => !value || row[key] === value) && Object.values(row).join(' ').toLowerCase().includes(searchTerm.toLowerCase()) && (!reference || Object.values(row).join(' ').includes(reference))), [entries, filters, reference, searchTerm])
  const total = entries.reduce((sum, entry) => sum + entry.amount, 0)
  function cell(row, key) {
    if (key === 'loan' && row.loanId != null) return <Link to={`/loans/${row.loanId}`}>{row.loan}</Link>
    if (['settlement', 'status'].includes(key)) return <FinanceState value={row[key]} />
    if (['debit', 'credit'].includes(key)) return <span className="finance-number">{money(row[key])}</span>
    return row[key]
  }
  return <section className="ref-page finance-page ledger-reference"><header className="ref-heading"><h1>Settlement Ledger </h1><span className="ledger-heading-divider" /></header><ReferenceMetrics metrics={[['▱', 'Total Debits', money(total)], ['▱', 'Total Credits', money(total)], ['⇄', 'Net Movement', '₹0.00'], ['▤', 'Unsettled Items', '0 (₹0.00)']]} />
    <ReferenceFilters fields={[['account', 'Account', 'All accounts'], ['type', 'Entry Type', 'All entry types'], ['borrower', 'Borrower', 'All borrowers'], ['loan', 'Loan', 'All loans'], ['settlement', 'Settlement State', 'All states']]} source={entries} draft={draft} setDraft={setDraft} apply={() => setFilters(draft)} clear={() => { setDraft(blank); setFilters(blank); setParams({}) }} extra={<label>Date Range<select aria-label="Date Range" defaultValue="All dates"><option>All dates</option></select></label>} />
    {reference && <div className="finance-reference-notice">Reference: {reference} <button className="ref-clear" type="button" onClick={() => setParams({})}>Show all entries</button></div>}
    {loading ? <p className="ref-empty" role="status">Loading live ledger entries…</p> : error ? <p className="live-error" role="alert">Live ledger unavailable: {error}</p> : <ReferenceRegister columns={columns} rows={rows} expanded={expanded} setExpanded={setExpanded} cell={cell} details={(row) => <JournalDetails row={row} />} className="finance-ledger-table" />}
  </section>
}
