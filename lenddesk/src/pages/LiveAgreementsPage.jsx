import { useEffect, useState } from 'react'
import { useOutletContext } from 'react-router-dom'
import { lenderGet, lenderPost } from '../api/lendDeskApi'
import { useDataSource } from '../hooks/useDataSource'
import ReferenceRegister from '../components/reference/ReferenceRegister'
import ReferenceTable, { Badge } from '../components/reference/ReferenceTable'
import './ReferencePages.css'

const label = value => value == null ? '—' : value.toLowerCase().replaceAll('_',' ').replace(/^./,c=>c.toUpperCase())
const date = value => value ? new Date(value.length===10?value+'T00:00:00Z':value).toLocaleDateString('en-US',{timeZone: 'Asia/Kolkata',month:'short',day:'numeric',year:'numeric'}) : '—'
const time = value => value ? new Date(value).toLocaleString('en-GB',{timeZone: 'Asia/Kolkata'}) : '—'
const number = value => value==null?'—':Number(value).toLocaleString('en-US')
const money = (value,currency='INR') => value==null?'—':new Intl.NumberFormat('en-IN',{style:'currency',currency,maximumFractionDigits:2}).format(value)
const tone = value => /ACTIVE|ACCEPTED|SETTLED|PAID/.test(value??'')?'good':/REJECTED|CLOSED|EXPIRED/.test(value??'')?'risk':'warn'
const columns=[['number','Agreement ID'],['borrowerName','Borrower'],['status','Status'],['effectiveDate','Effective date'],['expiryDate','Expiry date'],['feeRateBps','Lending fee rate'],['rebateSettlementFrequency','Rebate frequency'],['coveragePercent','Collateral ratio'],['recallAllowed','Recall'],['activeLoanCount','Active loans'],['totalExposure','Exposure']]

const today = () => new Date(Date.now() + 330 * 60000).toISOString().slice(0, 10)
const oneYearFromToday = () => {
 const value = new Date(Date.now() + 330 * 60000)
 value.setUTCFullYear(value.getUTCFullYear() + 1)
 return value.toISOString().slice(0, 10)
}
const decimalInput = value => Number(String(value).replace(/[₹,\s]/g, ''))

function AgreementOfferDialog({ organizationId, borrowers, onClose, onSent }) {
 const [form, setForm] = useState({
  connectionId: borrowers[0]?.connectionId ?? '', number: 'MSLA-DRAFT', effectiveDate: today(), expiryDate: oneYearFromToday(),
  creditLimit: '10000000', feeRate: '0.03', rebateRate: '0.01', coverageRatio: '1.05', billingFrequency: 'MONTHLY',
  rebateSettlementFrequency: 'AT_CLOSE', dayBasis: '365', recallAllowed: true, marginHours: '24', recallHours: '48', securityId: '', haircut: '0.10',
 })
 const [submitting, setSubmitting] = useState(false)
 const [error, setError] = useState('')
 const update = (key, value) => setForm(current => ({ ...current, [key]: value }))
 async function submit(event) {
  event.preventDefault()
  setError('')
  const creditLimit = decimalInput(form.creditLimit)
  if (!Number.isFinite(creditLimit) || creditLimit < 0.000001) return setError('Credit limit must be a positive amount. You may enter ₹ and comma separators, for example 10,000,000.')
  if (!form.connectionId) return setError('Select the borrower connection that will receive this offer.')
  if (form.expiryDate < form.effectiveDate) return setError('Expiry date cannot be before the effective date.')
  const rules = [{ assetType: 'CASH', securityId: null, haircut: 0 }]
  if (form.securityId) rules.push({ assetType: 'SECURITY', securityId: Number(form.securityId), haircut: Number(form.haircut) })
  setSubmitting(true)
  try {
   const agreement = await lenderPost(`/api/v1/organizations/${organizationId}/agreements`, {
    connectionId: Number(form.connectionId), number: form.number, effectiveDate: form.effectiveDate, expiryDate: form.expiryDate,
    creditLimit, feeRate: Number(form.feeRate), rebateRate: Number(form.rebateRate), coverageRatio: Number(form.coverageRatio),
    billingFrequency: form.billingFrequency, rebateSettlementFrequency: form.rebateSettlementFrequency, dayBasis: Number(form.dayBasis), recallAllowed: form.recallAllowed,
    marginHours: Number(form.marginHours), recallHours: Number(form.recallHours), collateralRules: rules,
   })
   onSent(agreement)
  } catch (requestError) { setError(requestError.message) } finally { setSubmitting(false) }
 }
 return <div className="agreement-offer-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !submitting) onClose() }}><section className="agreement-offer-dialog" role="dialog" aria-modal="true" aria-labelledby="send-msla-title"><header><div><p>COUNTERPARTY TERMS</p><h2 id="send-msla-title">Send MSLA offer</h2></div><button type="button" className="agreement-offer-close" onClick={onClose} disabled={submitting} aria-label="Close">×</button></header><p className="agreement-offer-intro">The borrower receives these terms as an offer and can only accept or reject them. No loan activity is permitted until both systems show <b>Active</b>.</p><form onSubmit={submit}><div className="agreement-offer-grid"><label>Borrower connection<select value={form.connectionId} onChange={event => update('connectionId', event.target.value)} required><option value="">Select borrower</option>{borrowers.map(b => <option key={b.connectionId} value={b.connectionId}>{b.name} ({b.externalId})</option>)}</select></label><label>Agreement reference<input value={form.number} maxLength="100" onChange={event => update('number', event.target.value)} required /></label><label>Effective date<input type="date" value={form.effectiveDate} onChange={event => update('effectiveDate', event.target.value)} required /></label><label>Expiry date<input type="date" value={form.expiryDate} onChange={event => update('expiryDate', event.target.value)} required /></label><label>Credit limit (₹)<input type="text" inputMode="decimal" placeholder="10,000,000" value={form.creditLimit} onChange={event => update('creditLimit', event.target.value)} required /></label><label>Fee rate (annual decimal)<input type="number" min="0" max="1" step="0.0001" value={form.feeRate} onChange={event => update('feeRate', event.target.value)} required /></label><label>Cash rebate rate (annual decimal)<input type="number" min="0" max="1" step="0.0001" value={form.rebateRate} onChange={event => update('rebateRate', event.target.value)} required /></label><label>Collateral ratio<input type="number" min="1" max="5" step="0.01" value={form.coverageRatio} onChange={event => update('coverageRatio', event.target.value)} required /></label><label>Fee billing<select value={form.billingFrequency} onChange={event => update('billingFrequency', event.target.value)}><option value="MONTHLY">Monthly</option><option value="AT_CLOSE">At loan termination</option></select></label><label>Rebate settlement<select value={form.rebateSettlementFrequency} onChange={event => update('rebateSettlementFrequency', event.target.value)}>{['DAILY','WEEKLY','MONTHLY','AT_CLOSE'].map(value => <option key={value} value={value}>{label(value)}</option>)}</select></label><label>Day basis<select value={form.dayBasis} onChange={event => update('dayBasis', event.target.value)}><option value="360">360</option><option value="365">365</option></select></label><label>Security collateral ID <small>Optional</small><input type="number" min="1" step="1" placeholder="Security ID" value={form.securityId} onChange={event => update('securityId', event.target.value)} /></label>{form.securityId && <label>Security haircut<input type="number" min="0" max="0.99999999" step="0.01" value={form.haircut} onChange={event => update('haircut', event.target.value)} required /></label>}<label>Margin notice (hours)<input type="number" min="1" step="1" value={form.marginHours} onChange={event => update('marginHours', event.target.value)} required /></label><label>Recall notice (hours)<input type="number" min="1" step="1" value={form.recallHours} onChange={event => update('recallHours', event.target.value)} required /></label><label className="agreement-offer-check"><input type="checkbox" checked={form.recallAllowed} onChange={event => update('recallAllowed', event.target.checked)} /> Recall permitted under this MSLA</label></div>{error && <p className="live-error" role="alert">{error}</p>}<footer><button type="button" onClick={onClose} disabled={submitting}>Cancel</button><button className="ref-primary" type="submit" disabled={submitting}>{submitting ? 'Sending…' : 'Send to borrower'}</button></footer></form></section></div>
}

function LiveAgreementDetails({id,organizationId,refreshKey}){
 const [tab,setTab]=useState('Collateral rules')
 const [state,setState]=useState({scope:'',detail:null,error:''})
 const scope=`${organizationId}:${id}:${refreshKey}`
 useEffect(()=>{
  const controller=new AbortController()
  lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/agreements/${id}/screen`,controller.signal)
   .then(detail=>{if(!controller.signal.aborted)setState({scope,detail,error:''})})
   .catch(error=>{if(!controller.signal.aborted)setState({scope,detail:null,error:error.message})})
  return ()=>controller.abort()
 },[organizationId,id,refreshKey,scope])
 if(state.scope!==scope)return <p className="ref-empty" role="status">Loading agreement details…</p>
 if(state.error)return <p className="live-error" role="alert">Agreement details unavailable: {state.error}</p>
 const detail=state.detail,a=detail.agreement
 const rules=<ReferenceTable emptyMessage="No records available." title={`Eligible collateral rules (${detail.collateralRules.length})`} headers={['Asset type','Security','Eligibility','Advance / Haircut','Concentration']} rows={detail.collateralRules.map(r=>[r.assetType,r.securitySymbol??'—',<Badge>{r.eligible?'Include':'Exclude'}</Badge>,`${number(r.advancePercent)}% / ${number(r.haircutPercent)}%`,r.concentrationLimitPercent==null?'Not stored':`${number(r.concentrationLimitPercent)}%`])}/>
 const loans=<ReferenceTable emptyMessage="No records available." title={`Active loans (${detail.activeLoanCount})`} headers={['Loan ID','Security','Outstanding quantity','Market value','Start date']} rows={detail.activeLoans.map(l=>[<span>{l.id}</span>,l.securitySymbol??`Security ${l.securityId}`,number(l.outstandingQuantity),<span title={l.priceAt?`Cached quote: ${time(l.priceAt)} IST`:'Quote timestamp unavailable'}>{money(l.marketValue,a.currency)}</span>,date(l.settledAt)])}/>
 const activity=<ReferenceTable emptyMessage="No records available." title={`Recent locates / reservations (${detail.recentActivityCount})`} headers={['Time IST','Type / ID','Security','Quantity','Status']} rows={detail.recentActivity.map(r=>[time(r.createdAt),`${label(r.type)} ${r.id}`,r.securitySymbol??`Security ${r.securityId}`,number(r.quantity),<Badge tone={tone(r.status)}>{label(r.status)}</Badge>])}/>
 return <div className="ref-agreement-details"><div className="ref-tabs" role="tablist" aria-label={`Agreement ${id} details`}>{['Terms','Collateral rules','Loans','Billing','Audit & FIX'].map(name=><button type="button" key={name} role="tab" aria-selected={tab===name} onClick={()=>setTab(name)}>{name}</button>)}</div>{tab==='Collateral rules'?<div className="ref-three-cards">{rules}{loans}{activity}</div>:tab==='Loans'?loans:tab==='Terms'?<ReferenceTable emptyMessage="No records available." title="Agreement terms" headers={['Term','Value']} rows={[
 ['Number',a.number],['Borrower',a.borrowerName??'—'],['Currency',a.currency],['Effective / expiry',`${date(a.effectiveDate)} / ${date(a.expiryDate)}`],['Credit limit',money(a.creditLimit,a.currency)],['Lending fee',`${number(a.feeRateBps)} bps`],['Rebate rate',`${number(a.rebateRateBps)} bps`],['Billing / rebate settlement',`${label(a.billingFrequency)} / ${label(a.rebateSettlementFrequency)}`],['Coverage',`${number(a.coveragePercent)}%`],['Day basis',a.dayBasis],['Recall',a.recallAllowed?'Enabled':'Disabled'],['Margin / recall notice hours',`${detail.terms.marginHours??'—'} / ${detail.terms.recallHours??'—'}`],['Jurisdiction / agreement framework','Not stored in lender service'],
 ]}/>:tab==='Billing'?<ReferenceTable emptyMessage="No records available." title={`Fee / rebate records (${detail.billingCount})`} headers={['Type / ID','Loan ID','Amount','Period','Due / payment date','Status']} rows={detail.billing.map(r=>[`${r.type} ${r.id}`,r.loanId,money(r.amount,a.currency),`${date(r.periodStart)} – ${date(r.periodEnd)}`,date(r.dueDate),<Badge tone={tone(r.status)}>{label(r.status)}</Badge>])}/>:<><ReferenceTable emptyMessage="No records available." title={`Agreement audit (${detail.auditCount})`} headers={['Time IST','Event','Reference']} rows={detail.audit.map(r=>[time(r.createdAt),label(r.type),r.reference])}/><p className="ref-empty">FIX message details belong to the adapter and are unavailable from this API.</p></>}{detail.activeLoanCount>detail.activeLoans.length&&<p className="ref-empty">Showing the latest {detail.activeLoans.length} active loans.</p>}</div>
}
export default function LiveAgreementsPage(){
 const {searchTerm=''}=useOutletContext()
 const {organizationId,refreshKey,refresh}=useDataSource()
 const [filters,setFilters]=useState({connectionId:'',status:'',recallAllowed:'',activeOn:''})
 const [expanded,setExpanded]=useState(null)
 const [state,setState]=useState({scope:'',data:null,error:''})
 const [offerOpen,setOfferOpen]=useState(false)
 const [notice,setNotice]=useState('')
 const query=new URLSearchParams(Object.entries({...filters,q:searchTerm}).filter(([,value])=>value!==''))
 const queryText=query.toString(),scope=`${organizationId}:${refreshKey}:${queryText}`
 useEffect(()=>{
  const controller=new AbortController()
  async function load(){
   try{
    let overview,rows=[],page=0
    do{
     overview=await lenderGet(`/api/v1/organizations/${organizationId}/lenddesk/agreements/overview?${queryText}&page=${page}&size=200`,controller.signal)
     rows.push(...overview.agreements.content)
     page++
    }while(page<overview.agreements.totalPages)
    if(!controller.signal.aborted)setState({scope,data:{...overview,rows},error:''})
   }catch(error){if(!controller.signal.aborted)setState({scope,data:null,error:error.message})}
  }
  const timer=setTimeout(load,150)
  return ()=>{clearTimeout(timer);controller.abort()}
 },[organizationId,refreshKey,queryText,scope])
 const loading=state.scope!==scope
 const data=loading?null:state.data
 const update=(key,value)=>setFilters(current=>({...current,[key]:value}))
 function cell(row,key){
  if(key==='status')return <Badge tone={tone(row.status)}>{label(row.status)}</Badge>
  if(key==='effectiveDate'||key==='expiryDate')return date(row[key])
  if(key==='feeRateBps')return `${number(row[key])} bps`
  if(key==='rebateSettlementFrequency')return label(row[key])
  if(key==='coveragePercent')return `${number(row[key])}%`
  if(key==='recallAllowed')return <Badge tone={row[key]?'good':'warn'}>{row[key]?'Enabled':'Disabled'}</Badge>
  if(key==='totalExposure')return <span title={row.unpricedLoanCount?`${row.unpricedLoanCount} unpriced loans`:undefined}>{money(row[key],row.currency)}</span>
  if(key==='number')return <button type="button" className="ref-id" onClick={()=>setExpanded(current=>current===row.id?null:row.id)}>{row.number}</button>
  return row[key]??'—'
 }
 return <section className="ref-page ref-agreements live-agreements-page"><header className="ref-heading"><h1>MSLA Agreements</h1><span className="ref-active-count">{loading?'Loading…':state.error?'Unavailable':`${data?.activeAgreementCount??0} active`}</span><button type="button" className="ref-primary" onClick={()=>{setNotice('');setOfferOpen(true)}} disabled={loading||Boolean(state.error)}>Send MSLA offer</button></header>{notice&&<p className="live-agreement-notice" role="status">{notice}</p>}<section className="ref-filters" aria-label="Live agreement filters"><label>Borrower<select aria-label="Borrower" value={filters.connectionId} onChange={event=>update('connectionId',event.target.value)}><option value="">All borrowers</option>{data?.borrowers.map(b=><option key={b.connectionId} value={b.connectionId}>{b.name} ({b.externalId})</option>)}</select></label><label>Status<select aria-label="Status" value={filters.status} onChange={event=>update('status',event.target.value)}><option value="">All statuses</option>{data?.statuses.map(status=><option key={status}>{status}</option>)}</select></label><label>Jurisdiction<select aria-label="Jurisdiction" disabled><option>Not stored</option></select></label><label>Recall enabled<select aria-label="Recall enabled" value={filters.recallAllowed} onChange={event=>update('recallAllowed',event.target.value)}><option value="">All</option><option value="true">Enabled</option><option value="false">Disabled</option></select></label><label>Active on<input aria-label="Agreement active on date" type="date" value={filters.activeOn} onChange={event=>update('activeOn',event.target.value)}/></label></section>{loading?<p className="ref-empty" role="status">Loading live agreements…</p>:state.error?<p className="live-error" role="alert">Live agreements unavailable: {state.error}</p>:<ReferenceRegister columns={columns} rows={data?.rows??[]} expanded={expanded} setExpanded={setExpanded} cell={cell} details={row=><LiveAgreementDetails id={row.id} organizationId={organizationId} refreshKey={refreshKey}/>} className="ref-agreements-table"/>}{offerOpen&&<AgreementOfferDialog organizationId={organizationId} borrowers={data?.borrowers??[]} onClose={()=>setOfferOpen(false)} onSent={agreement=>{setOfferOpen(false);setNotice(`MSLA ${agreement.number} was sent to the borrower and is awaiting their response.`);refresh()}}/>}</section>
}
