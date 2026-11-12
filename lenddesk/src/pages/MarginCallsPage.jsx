import { useMemo, useState } from 'react'
import { useNavigate, useOutletContext } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { useLenderData } from '../hooks/useLenderData'
import { useDataSource } from '../hooks/useDataSource'

const demo = [
 {id:'MC-000043',loan:'LN-000250',security:'HDFCBANK · NSE',lender:'Pioneer Securities',required:375000,fulfilled:150000,due:'2026-11-12T15:30:00',status:'Partially fulfilled',marketValue:3500000,ratio:110,requiredCollateral:3850000,recognized:3475000,shortfall:375000,topup:150000,remaining:225000},
 {id:'MC-000044',loan:'LN-000249',security:'RELIANCE · NSE',lender:'Meridian Mutual Fund',required:250000,fulfilled:0,due:'2026-11-13T11:00:00',status:'Open',marketValue:2250000,ratio:110,requiredCollateral:2475000,recognized:2225000,shortfall:250000,topup:0,remaining:250000},
 {id:'MC-000042',loan:'LN-000252',security:'ICICIBANK · NSE',lender:'Alpha Asset Mgmt',required:125000,fulfilled:125000,due:'2026-11-11T14:00:00',status:'Fulfilled',marketValue:1125000,ratio:110,requiredCollateral:1237500,recognized:1237500,shortfall:125000,topup:125000,remaining:0},
 {id:'MC-000041',loan:'LN-000251',security:'TCS · NSE',lender:'Crest Capital',required:210000,fulfilled:210000,due:'2026-11-10T16:00:00',status:'Fulfilled',marketValue:1900000,ratio:110,requiredCollateral:2090000,recognized:2090000,shortfall:210000,topup:210000,remaining:0},
 {id:'MC-000040',loan:'LN-000248',security:'INFY · NSE',lender:'Alpha Asset Mgmt',required:100000,fulfilled:100000,due:'2026-11-09T14:30:00',status:'Fulfilled',marketValue:900000,ratio:110,requiredCollateral:990000,recognized:990000,shortfall:100000,topup:100000,remaining:0}
]
const money = n => '₹' + Number(n || 0).toLocaleString('en-IN')
const dateText = d => new Date(d).toLocaleString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false})
const tone = s => /fulfilled/i.test(s) ? 'good' : /partial/i.test(s) ? 'warn' : 'risk'
const start = d => new Date(d.getFullYear(),d.getMonth(),d.getDate())
function inDateRange(value,filter) {
 const d=new Date(value), now=new Date(), today=start(now)
 if(filter==='today') return d>=today && d<new Date(today.getTime()+86400000)
 if(filter==='tomorrow') return d>=new Date(today.getTime()+86400000) && d<new Date(today.getTime()+172800000)
 if(filter==='week') { const end=new Date(today); end.setDate(end.getDate()+7); return d>=today && d<end }
 if(filter==='month') return d.getFullYear()===now.getFullYear() && d.getMonth()===now.getMonth()
 if(filter==='overdue') return d<now
 return true
}
export default function MarginCallsPage() {
 const {searchTerm=''}=useOutletContext()
 const navigate=useNavigate()
 const {mode}=useDataSource()
 const {data:liveData,loading,error}=useLenderData('margin-calls')
 const [status,setStatus]=useState('all'),[due,setDue]=useState('all'),[security,setSecurity]=useState('')
 const [selectedId,setSelectedId]=useState('MC-000043'),[tab,setTab]=useState('Coverage')
 const [collateral,setCollateral]=useState('Cash'),[amount,setAmount]=useState('225000')
 const source=mode==='live' && Array.isArray(liveData) ? liveData.map(r=>({...r,id:r.callId??r.id,loan:r.loanId??r.loan,security:r.securitySymbol??r.security,lender:r.lenderName??r.lender,required:Number(r.requiredAmount??r.required??0),fulfilled:Number(r.fulfilledAmount??r.fulfilled??0),due:r.dueAt??r.dueDate??r.due,status:r.status??'Open',marketValue:Number(r.loanMarketValue??r.marketValue??0),ratio:Number(r.requiredRatio??r.ratio??110),requiredCollateral:Number(r.requiredCollateral??0),recognized:Number(r.recognizedCollateral??0),shortfall:Number(r.initialShortfall??r.requiredAmount??0),topup:Number(r.confirmedTopup??r.fulfilledAmount??0),remaining:Number(r.remainingResponse??Math.max(0,(r.requiredAmount??0)-(r.fulfilledAmount??0)))})) : demo
 const rows=useMemo(()=>source.filter(r=>{
  const st=status==='all'||(status==='open'?/open|partial/i.test(r.status):status==='fulfilled'?/fulfilled/i.test(r.status):new Date(r.due)<new Date()&&!/fulfilled|closed/i.test(r.status))
  const q=[r.id,r.loan,r.security,r.lender].join(' ').toLowerCase()
  return st&&(due==='all'||inDateRange(r.due,due))&&q.includes(security.toLowerCase())&&q.includes(searchTerm.toLowerCase())
 }),[source,status,due,security,searchTerm])
 const selected=rows.find(r=>r.id===selectedId)||rows[0]
 const open=source.filter(r=>/open|partial/i.test(r.status))
 const dueToday=open.filter(r=>inDateRange(r.due,'today'))
 const overdue=source.filter(r=>new Date(r.due)<new Date()&&!/fulfilled|closed/i.test(r.status)).length
 return <div className="margin-page">
  <section className="heading margin-heading"><div><h1>Margin calls</h1><p>Review collateral shortfalls and submit additional collateral</p></div><div className="margin-heading-actions"><button className="outline-action" onClick={()=>window.location.reload()} type="button">↻ Refresh</button><button className="margin-primary" onClick={()=>navigate('/valuations')} type="button">▤ View valuations</button></div></section>
  {dueToday.length>0&&<div className="margin-warning"><b>⚠ {dueToday.length} open margin call{dueToday.length===1?'':'s'} due today</b><span>Earliest due today at {dateText(dueToday.sort((a,b)=>new Date(a.due)-new Date(b.due))[0].due).split(', ')[1]} IST</span></div>}
  <section className="margin-summary"><div>Open <b>{open.length}</b></div><div>Required top-up <strong>{money(open.reduce((s,r)=>s+r.required,0))}</strong></div><div>Fulfilled <strong>{money(open.reduce((s,r)=>s+r.fulfilled,0))}</strong></div><div>Overdue <strong>{overdue}</strong></div></section>
  <section className="margin-filters"><label>Status<select value={status} onChange={e=>setStatus(e.target.value)}><option value="all">All</option><option value="open">Open</option><option value="fulfilled">Fulfilled</option><option value="overdue">Overdue</option></select></label><label>Lender<select><option>All counterparties</option>{[...new Set(source.map(r=>r.lender))].map(x=><option key={x}>{x}</option>)}</select></label><label className="margin-security">Security<input value={security} onChange={e=>setSecurity(e.target.value)} placeholder="⌕ Search symbol"/></label><label>Due date<select value={due} onChange={e=>setDue(e.target.value)}><option value="all">All dates</option><option value="today">Today</option><option value="tomorrow">Tomorrow</option><option value="week">This week</option><option value="month">This month</option><option value="overdue">Overdue</option></select></label><button className="margin-clear" onClick={()=>{setStatus('all');setDue('all');setSecurity('')}} type="button">Clear filters</button></section>
  <section className="panel margin-register"><div className="scroll"><table><thead><tr><th>Call ID</th><th>Loan</th><th>Security</th><th>Lender</th><th className="num">Required amount</th><th className="num">Fulfilled amount</th><th>Due at</th><th>Status</th></tr></thead><tbody>{rows.map(r=><tr key={r.id} className={selected&&selected.id===r.id?'selected':''} onClick={()=>{setSelectedId(r.id);setTab('Coverage');setAmount(String(r.remaining))}}><td><a>{r.id}</a></td><td>{r.loan}</td><td><b>{r.security}</b></td><td>{r.lender}</td><td className="num">{money(r.required)}</td><td className="num">{money(r.fulfilled)}</td><td>{dateText(r.due)}</td><td><StatusBadge tone={tone(r.status)}>{r.status}</StatusBadge></td></tr>)}</tbody></table></div><footer>{loading?'Loading margin calls…':error?'Live API unavailable: '+error:rows.length+' calls'}</footer></section>
  {selected&&<section className="margin-detail panel"><div className="margin-detail-title"><h2>{selected.id} / {selected.security.split(' · ')[0]}</h2><StatusBadge tone={tone(selected.status)}>{selected.status}</StatusBadge></div><div className="margin-tabs" role="tablist">{['Coverage','Responses','Events'].map(t=><button key={t} type="button" className={tab===t?'active':''} onClick={()=>setTab(t)}>{t}</button>)}</div>
   {tab==='Coverage'&&<div className="margin-coverage-layout"><div className="margin-coverage"><h3>Coverage calculation</h3>{[['Loan market value',money(selected.marketValue)],['Required ratio',selected.ratio+'%'],['Required collateral',money(selected.requiredCollateral)],['Recognized collateral (current)',money(selected.recognized)],['Initial shortfall',money(selected.shortfall)],['Confirmed top-up (this call)',money(selected.topup)],['Remaining response',money(selected.remaining)]].map((x,i)=><div key={x[0]} className={i===4||i===6?'emphasis':''}><span>{x[0]}</span><b>{x[1]}</b></div>)}</div><div className="margin-response"><h3>Respond with collateral</h3><label>Collateral type<select value={collateral} onChange={e=>setCollateral(e.target.value)}><option>Cash</option><option>Government securities</option><option>Equity securities</option></select></label><label>Amount<input value={amount} onChange={e=>setAmount(e.target.value)}/></label><label>Linked loan<select disabled value={selected.loan}><option>{selected.loan}</option></select></label><label>Margin call<select disabled value={selected.id}><option>{selected.id}</option></select></label><button className="margin-primary" type="button" onClick={()=>window.alert('Collateral response recorded in the demo workflow.')}>Submit collateral response</button><small>ⓘ Response stays pending until collateral receipt and obligation confirmation.</small></div></div>}
   {tab==='Responses'&&<div className="margin-tab-content"><p>Collateral response history for {selected.id}.</p><p>{selected.fulfilled>0?money(selected.fulfilled)+' fulfilled against '+money(selected.required):'No collateral responses recorded for this call.'}</p></div>}
   {tab==='Events'&&<div className="margin-tab-content"><p>Call created · {dateText(selected.due)}</p><p>{selected.status} · Remaining response {money(selected.remaining)}</p></div>}
  </section>}
  <div className="margin-connectivity"><i/> Borrower service connected <span>│</span><i/> FIX session online <span>│</span> {mode==='live'?'Live data':'Demo data'} · Last refreshed {new Date().toLocaleTimeString('en-IN',{hour:'2-digit',minute:'2-digit',second:'2-digit'})} IST</div>
 </div>
}
