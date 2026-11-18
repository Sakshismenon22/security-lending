import TablePagination from '../../components/TablePagination';
import useCollateralReleaseUpdates from '../../hooks/useCollateralReleaseUpdates';
import CollateralReleaseStatus from '../../components/collateral/CollateralReleaseStatus';
import CollateralReleaseRequest from '../../components/collateral/CollateralReleaseRequest';
import{useCallback,useEffect,useMemo,useState}from'react';
import{useNavigate,useParams}from'react-router-dom';
import{ArrowLeft,ArrowDownUp,CalendarDays,CheckCircle2,ChevronRight,Clock3,Eye,FileText,MoreHorizontal,Plus,RefreshCw,RotateCcw,ShieldCheck,Wallet,X}from'lucide-react';
import{lendingApi}from'../../api/lendingApi';
import{unwrapRows}from'../../api/apiClient';
import'./LoanWorkspace.css';

const rows=x=>Array.isArray(x)?x:unwrapRows(x);
const val=(o,...keys)=>{for(const k of keys){const v=o?.[k];if(v!==undefined&&v!==null&&v!=='')return v}return null};
const key=x=>String(x??'');
const n=x=>x==null||x===''?'-':Number(x).toLocaleString('en-IN',{maximumFractionDigits:2});
const money=x=>x==null||x===''?'-':'₹'+Number(x).toLocaleString('en-IN',{maximumFractionDigits:2});
const pct=x=>x==null||x===''?'-':n(x)+'%';
const date=x=>{if(!x)return'-';const d=new Date(x);return Number.isNaN(+d)?'-':d.toLocaleDateString('en-IN',{day:'2-digit',month:'short',year:'numeric',timeZone:'Asia/Kolkata'})};
const stamp=x=>{if(!x)return'-';const d=new Date(x);return Number.isNaN(+d)?'-':d.toLocaleString('en-IN',{day:'2-digit',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',hour12:false,timeZone:'Asia/Kolkata'})};
const state=x=>String(x||'-').replaceAll('_',' ').toUpperCase();
const statusTone=x=>{const s=state(x);return ['ACTIVE','COMPLETED','SETTLED','POSTED'].includes(s)?'good':s.includes('MARGIN')||s.includes('PENDING')||s.includes('ACCRUED')?'warn':s.includes('RECALL')||s.includes('OVERDUE')||s.includes('DEFAULT')?'bad':'neutral'};
const labelId=x=>x==null?'-':/^LN-/i.test(String(x))?String(x):'LN-'+String(x).padStart(6,'0');
const feePercent=x=>x==null?null:Number(x)>20?Number(x)/100:Number(x)<=1?Number(x)*100:Number(x);
const bps=x=>x==null?'-':n(feePercent(x)*100)+' bps';
const getRows=(obj,...names)=>{for(const name of names){const x=obj?.[name];if(Array.isArray(x))return x;if(x&&Array.isArray(x.content))return x.content}return[]};

function Field({label,value,good=false}){return <div className="lw-field"><span>{label}</span><b className={good?'positive':''}>{value??'-'}</b></div>}
function Badge({value}){return <span className={'lw-badge '+statusTone(value)}>{state(value)}</span>}
function Timeline({events,loading=false}){return <div className="lw-timeline">{loading&&<div className="lw-empty">Loading activity…</div>}{!loading&&!events.length&&<div className="lw-empty">No event history available for this loan.</div>}{events.map((e,i)=><div className="lw-event" key={val(e,'id')||i}><span className={'lw-event-dot '+statusTone(val(e,'status','eventType','event_type'))}></span><div className="lw-event-main"><div className="lw-event-title">{String(val(e,'_label','eventType','event_type','type','action','description')||'Loan event').replaceAll('_',' ')}</div><small>{stamp(val(e,'createdAt','created_at','eventTime','event_time','postedAt','posted_at','requestedAt','requested_at','requestTime','request_time','accrualDate','accrual_date'))}{val(e,'quantity','amount','feeAmount','fee_amount')!=null?' · ':''}{val(e,'quantity')!=null?n(val(e,'quantity'))+' shares':''}{val(e,'amount','feeAmount','fee_amount')!=null?money(val(e,'amount','feeAmount','fee_amount')):''}{val(e,'description','remarks','reference')?' · '+val(e,'description','remarks','reference'):''}</small></div><Badge value={val(e,'status','eventStatus','event_status')||'RECORDED'}/></div>)}</div>}

export default function LoanWorkspace(){
 const{loanId}=useParams(),navigate=useNavigate();
 const[data,setData]=useState({loan:null,agreements:[],lenders:[],securities:[],collateral:[],returns:[],recalls:[],fees:[],audit:[],marginCalls:[]});
 const[lastRefreshed,setLastRefreshed]=useState(null);
 const[loading,setLoading]=useState(true),[error,setError]=useState(''),[tab,setTab]=useState('Overview'),[refreshing,setRefreshing]=useState(false);
 const[modal,setModal]=useState(''),[assetType,setAssetType]=useState('CASH'),[cashAmount,setCashAmount]=useState(''),[securityId,setSecurityId]=useState(''),[securityQty,setSecurityQty]=useState(''),[quantity,setQuantity]=useState(''),[busy,setBusy]=useState(false),[notice,setNotice]=useState('');
 const load=useCallback(async(showLoader=true)=>{if(showLoader)setLoading(true);setError('');const names=['loan','agreements','lenders','securities','collateral','returns','recalls','fees','audit','marginCalls'];const requests=[lendingApi.loan(loanId),lendingApi.agreements(),lendingApi.lenders(),lendingApi.securities(),lendingApi.collateral(),lendingApi.returns(),lendingApi.recalls(),lendingApi.fees(),lendingApi.audit(),lendingApi.marginCalls()];const result=await Promise.allSettled(requests);setData(prev=>{const next={...prev};result.forEach((r,i)=>{if(r.status==='fulfilled')next[names[i]]=i===0?r.value:rows(r.value)});if(result[0].status==='rejected')next.loan=null;return next});if(result[0].status==='rejected')setError(result[0].reason?.message||'Unable to load this loan');setLoading(false);setRefreshing(false);if(result[0].status==='fulfilled')setLastRefreshed(new Date())},[loanId]);
 useEffect(()=>{load(true)},[loanId]);
 useCollateralReleaseUpdates(data.collateral,load);
 const loan=data.loan?.loan??data.loan?.data??data.loan;
 const loanCode=labelId(val(loan,'loanCode','loan_code','loanId','loan_id','id')??loanId);
 const security=data.securities.find(s=>key(val(s,'id','securityId','security_id'))===key(val(loan,'securityId','security_id')));
 const agreement=data.agreements.find(a=>key(val(a,'id'))===key(val(loan,'mslaId','msla_id','agreementId','agreement_id')));
 const lender=data.lenders.find(l=>key(val(l,'id'))===key(val(agreement,'lenderId','lender_id')));
 const securityName=val(security,'symbol','isin')||val(loan,'symbol','securitySymbol','security_symbol')||'-';
 const lenderName=val(lender,'name')||val(loan,'lenderName','lender_name')||'-';
 const outstanding=val(loan,'outstandingQuantity','outstanding_quantity');
 const original=val(loan,'originalQuantity','original_quantity','quantity');
 const price=val(security,'price','ltp')??val(loan,'loanPrice','loan_price','price');
 const market=val(loan,'marketValue','market_value')??(outstanding!=null&&price!=null?Number(outstanding)*Number(price):null);
 const feeRate=val(loan,'feeRate','fee_rate')??val(agreement,'feeRate','fee_rate');
 const feeAmount=val(loan,'accruedFee','accrued_fee');
 const maturity=val(loan,'maturityAt','maturity_at','maturityDate','maturity_date');
 const nestedCollateral=getRows(data.loan,'collateral_position','collateralPosition');
 const collateral=nestedCollateral.length?nestedCollateral:data.collateral.filter(x=>key(val(x,'loanId','loan_id'))===key(loanId));
 const returnsNested=getRows(data.loan,'loan_return','loanReturn');
 const returns=returnsNested.length?returnsNested:data.returns.filter(x=>key(val(x,'loanId','loan_id'))===key(loanId));
 const recallsNested=getRows(data.loan,'recall');
 const recalls=recallsNested.length?recallsNested:data.recalls.filter(x=>key(val(x,'loanId','loan_id'))===key(loanId));
 const feesNested=getRows(data.loan,'lending_fee','lendingFee');
 const fees=feesNested.length?feesNested:data.fees.filter(x=>key(val(x,'loanId','loan_id'))===key(loanId));
 const valuation=getRows(data.loan,'valuation')[0]||data.loan?.valuation||{};
 const positions=collateral.filter(x=>!['RELEASED','REJECTED','CANCELLED'].includes(state(val(x,'status'))));
 const posted=val(valuation,'collateralValue','collateral_value')??positions.reduce((sum,p)=>{const cash=state(val(p,'assetType','asset_type'))==='CASH'?val(p,'cashAmount','cash_amount'):null,qty=val(p,'quantity'),sec=data.securities.find(s=>key(val(s,'id','securityId','security_id'))===key(val(p,'securityId','security_id'))),mv=val(p,'marketValue','market_value')??(qty!=null&&val(sec,'price')!=null?Number(qty)*Number(val(sec,'price')):null);return sum+(cash!=null?Number(cash):mv!=null?Number(mv):0)},0);
 const eligible=val(valuation,'recognizedCollateral','recognized_collateral','eligibleCollateral','eligible_collateral')??positions.reduce((sum,p)=>{const explicit=val(p,'eligibleValue','eligible_value'),cash=state(val(p,'assetType','asset_type'))==='CASH'?val(p,'cashAmount','cash_amount'):null,qty=val(p,'quantity'),sec=data.securities.find(s=>key(val(s,'id','securityId','security_id'))===key(val(p,'securityId','security_id'))),mv=val(p,'marketValue','market_value')??(qty!=null&&val(sec,'price')!=null?Number(qty)*Number(val(sec,'price')):null),haircut=val(p,'haircut');const raw=cash!=null?Number(cash):mv!=null?Number(mv):0;return sum+(explicit!=null?Number(explicit):raw*(haircut==null?1:1-Number(haircut)))},0);
 const rawHaircut=val(valuation,'haircut','averageHaircut','average_haircut');const haircut=rawHaircut!=null?(Number(rawHaircut)<=1?Number(rawHaircut)*100:Number(rawHaircut)):(positions.length?positions.reduce((s,p)=>s+Number(val(p,'haircut')||0),0)/positions.length*100:null);
 const coverage=(val(valuation,'coverageRatio','coverage_ratio')!=null?Number(val(valuation,'coverageRatio','coverage_ratio'))*100:null)??val(valuation,'actualCoverage','actual_coverage','coverage')??(market&&Number(market)>0?Number(eligible)/Number(market)*100:null);
 const requiredRatio=val(agreement,'coverageRatio','coverage_ratio','collateralRatio','collateral_ratio');
 const requiredCollateral=val(valuation,'requiredCollateral','required_collateral')??(market&&requiredRatio!=null?Number(market)*Number(requiredRatio):null);
 const coverageSufficient=coverage!=null&&requiredRatio!=null&&coverage>=Number(requiredRatio)*100;
 const excess=val(valuation,'excessCollateral','excess_collateral')??(eligible!=null&&requiredCollateral!=null?Number(eligible)-Number(requiredCollateral):null);
 const openRecall=recalls.some(r=>!['CLOSED','SETTLED','COMPLETED','REJECTED'].includes(state(val(r,'status'))));
 const latestReturn=returns[0];
 const detailStatus=val(loan,'status')||'ACTIVE';
 const collateralType=positions.some(p=>state(val(p,'assetType','asset_type'))==='CASH')&&positions.some(p=>val(p,'securityId','security_id')!=null)?'Cash + securities':positions.some(p=>state(val(p,'assetType','asset_type'))==='CASH')?'Cash':positions.some(p=>val(p,'securityId','security_id')!=null)?'Securities':'-';
 const events=useMemo(()=>{const audit=data.audit.filter(e=>[val(e,'reference','referenceId','reference_id'),val(e,'entityId','entity_id')].some(x=>key(x)===key(loanId)));const all=[...audit,...positions.map(x=>({...x,_label:'Collateral posted'})),...returns.map(x=>({...x,_label:'Return requested'})),...recalls.map(x=>({...x,_label:'Recall issued'})),...fees.map(x=>({...x,_label:'Fee accrued'})),...data.marginCalls.filter(x=>key(val(x,'loanId','loan_id'))===key(loanId)).map(x=>({...x,_label:'Margin check'}))];const seen=new Set();return all.sort((a,b)=>new Date(val(a,'createdAt','created_at','eventTime','event_time')||0)-new Date(val(b,'createdAt','created_at','eventTime','event_time')||0)).filter(e=>{const id=key(val(e,'id'))+'|'+key(val(e,'_label'));if(seen.has(id))return false;seen.add(id);return true})},[data.audit,data.marginCalls,positions,returns,recalls,fees,loanId]);
 const detailFields=[['Security',val(security,'symbol')&&val(security,'name','companyName','company_name')?securityName+' ('+String(val(security,'name','companyName','company_name'))+')':securityName],['Lender',lenderName],['Agreement',val(agreement,'agreementNumber','agreement_number','number','id')],['Settlement set',val(loan,'settlementReference','settlement_reference','settlementSet','settlement_set')],['Original quantity',original!=null?n(original)+' shares':'-'],['Outstanding quantity',outstanding!=null?n(outstanding)+' shares':'-'],['Loan price (LTP)',money(price)],['Market value',money(market)],['Fee rate',feeRate==null?'-':n(feePercent(feeRate))+'% p.a. ('+bps(feeRate)+')'],['Accrued fee',money(feeAmount)],['Maturity (IST)',maturity?stamp(maturity):'-'],['Recall notice',val(agreement,'recallNotice','recall_notice')||'None'],['Required ratio',requiredRatio==null?'-':pct(Number(requiredRatio)<=2?Number(requiredRatio)*100:requiredRatio)],['Required collateral',money(requiredCollateral)],['Excess collateral',money(excess)]];
 const returnFields=[['Open recall',openRecall?'Yes':'No'],['Next return due',date(val(latestReturn,'dueAt','due_at'))],['Min. return qty',val(latestReturn,'minimumQuantity','minimum_quantity')!=null?n(val(latestReturn,'minimumQuantity','minimum_quantity')):'-'],['Max. return qty',outstanding!=null?n(outstanding):'-'],['Notice period',val(agreement,'recallNotice','recall_notice')||'-'],['Settlement cycle',val(agreement,'settlementCycle','settlement_cycle')||'-'],['Partial return allowed',val(agreement,'partialReturnAllowed','partial_return_allowed')===true?'Yes':val(agreement,'partialReturnAllowed','partial_return_allowed')===false?'No':'-'],['Returnable quantity',outstanding!=null?n(outstanding):'-'],['Return via',val(latestReturn,'settlementMethod','settlement_method')||'Market / off-market'],['Estimated return value',money(market)],['Estimated fee reversal',money(val(latestReturn,'feeReversal','fee_reversal'))]];
 const submit=async e=>{e.preventDefault();setBusy(true);try{if(modal==='return')await lendingApi.requestReturn(loanId,{quantity:Number(quantity)});else await lendingApi.postCollateral(loanId,{assetType,securityId:assetType==='SECURITY'?Number(securityId):null,quantity:assetType==='SECURITY'?Number(securityQty):0,cashAmount:assetType==='CASH'?Number(cashAmount):0});setNotice(modal==='return'?'Return request submitted':'Collateral posting submitted');setModal('');await load(false)}catch(err){setNotice(err.message||'Unable to submit request')}finally{setBusy(false)}};
 const tabs=['Overview','Collateral','Returns & recalls','Fees','Activity'];
 if(loading)return <main className="loan-workspace"><div className="lw-loading"><RefreshCw size={17}/>Loading loan workspace…</div></main>;
 if(error||!loan)return <main className="loan-workspace"><button className="lw-back" onClick={()=>navigate('/loans')}><ArrowLeft size={14}/>Back to Loans</button><div className="lw-error">{error||'Loan not found.'}</div></main>;
 return <main className="loan-workspace">
  <div className="lw-breadcrumb"><button onClick={()=>navigate('/loans')}>Loans</button><ChevronRight size={12}/><b>{loanCode}</b><ChevronRight size={12}/><span>{securityName}</span></div>
  <button className="lw-back" onClick={()=>navigate('/loans')}><ArrowLeft size={14}/>Back to Loans</button>
  <header className="lw-heading"><div className="lw-heading-title"><h1>{loanCode} / {securityName}</h1><Badge value={detailStatus}/></div><div className="lw-actions"><button className="lw-primary" disabled={!['ACTIVE','RETURN_PENDING'].includes(state(detailStatus))} onClick={()=>{setQuantity(String(outstanding||''));setModal('return')}}><RotateCcw size={13}/>Request return</button><button className="lw-outline" onClick={()=>{setAssetType('CASH');setCashAmount('');setSecurityId('');setSecurityQty('');setModal('collateral')}}><Plus size={13}/>Add collateral</button><CollateralReleaseRequest loanId={loanId} disabled={!['ACTIVE','RETURN_PENDING','CLOSED'].includes(state(detailStatus))} onComplete={message=>{setNotice(message);load(false)}}/><button className="lw-icon-btn" title="Refresh loan" onClick={()=>{setRefreshing(true);load(false)}}><RefreshCw size={14}/></button></div></header>
  <CollateralReleaseStatus positions={collateral}/>
  <section className="lw-summary"><div><span>Outstanding quantity</span><b>{n(outstanding)}</b><small>shares</small></div><div><span>Annual fee</span><b>{feeRate==null?'-':n(feePercent(feeRate))+'%'}</b></div><div><span>Coverage</span><b className={coverageSufficient?'positive':'negative'}>{pct(coverage)}</b></div><div><span>Lender</span><b>{lenderName}</b></div><div><span>Loan status</span><Badge value={detailStatus}/></div></section>
  <nav className="lw-tabs">{tabs.map(t=><button key={t} className={tab===t?'active':''} onClick={()=>setTab(t)}>{t}</button>)}</nav>
  {tab==='Overview'&&<section className="lw-overview">
   <div className="lw-column">
    <article className="lw-panel"><h2>Loan terms</h2><div className="lw-fields">{detailFields.map(([label,value])=><Field key={label} label={label} value={value} good={label==='Excess collateral'&&excess>0}/>)}</div></article>
    <article className="lw-panel"><h2>Return / recall obligations</h2><div className="lw-fields">{returnFields.map(([label,value])=><Field key={label} label={label} value={value}/>)}</div></article>
   </div>
   <div className="lw-column">
    <article className="lw-panel"><h2>Collateral & coverage</h2><div className="lw-fields"><Field label="Collateral type" value={collateralType}/><Field label="Collateral posted" value={money(posted)}/><Field label="Collateral value" value={money(posted)}/><Field label="Haircut" value={pct(haircut)}/><Field label="Eligible value" value={money(eligible)}/><Field label="Coverage" value={pct(coverage)} good={coverageSufficient}/><Field label="Coverage status" value={coverage==null?'-':coverageSufficient?'Coverage sufficient':'Shortfall'} good={coverageSufficient}/></div><div className="lw-subheading">Collateral lots ({positions.length})</div><CollateralTable positions={positions} securities={data.securities}/></article>
    <article className="lw-panel"><h2>Event timeline</h2><Timeline events={events.slice(0,6)}/></article>
   </div>
  </section>}
  {tab==='Collateral'&&<section className="lw-panel lw-tab-panel"><div className="lw-panel-heading"><h2>Collateral lots ({positions.length})</h2><button className="lw-outline small" onClick={()=>setModal('collateral')}><Plus size={12}/>Add collateral</button></div><CollateralTable positions={positions} securities={data.securities} full/></section>}
  {tab==='Returns & recalls'&&<section className="lw-tab-grid"><article className="lw-panel"><h2>Return obligations</h2><div className="lw-fields">{returnFields.map(([label,value])=><Field key={label} label={label} value={value}/>)}</div></article><article className="lw-panel"><h2>Return requests</h2><RecordTable items={returns} columns={['Request ID','Quantity','Due date','Status']} fields={['id','quantity','dueAt','status']}/></article><article className="lw-panel"><h2>Recall history</h2><RecordTable items={recalls} columns={['Recall ID','Quantity','Notice date','Status']} fields={['id','quantity','requestTime','status']}/></article></section>}
  {tab==='Fees'&&<section className="lw-panel lw-tab-panel"><div className="lw-panel-heading"><h2>Fee accruals ({fees.length})</h2><b className="lw-total">{money(fees.reduce((s,f)=>s+Number(val(f,'feeAmount','fee_amount')||0),0))}</b></div><RecordTable items={fees} columns={['Fee ID','Period','Rate','Amount','Status']} fields={['id','period','feeRate','feeAmount','status']}/></section>}
  {tab==='Activity'&&<section className="lw-panel lw-tab-panel"><h2>Settlement & activity history</h2><Timeline events={events}/></section>}
  <div className="lw-refresh-note"><RefreshCw size={13}/>{refreshing?"Refreshing…":`Last refreshed: ${stamp(lastRefreshed)} IST`}</div>
  {notice&&<button className="lw-toast" onClick={()=>setNotice('')}>{notice} ×</button>}
  {modal&&<div className="lw-modal-backdrop" onClick={()=>setModal('')}><form className="lw-modal" onSubmit={submit} onClick={e=>e.stopPropagation()}><header><h2>{modal==='return'?'Request return':'Add collateral'}</h2><button type="button" onClick={()=>setModal('')}><X size={15}/></button></header><p>{loanCode} · {securityName}</p>{modal==='return'?<label>Return quantity (shares)<input type="number" min="1" max={outstanding||undefined} required value={quantity} onChange={e=>setQuantity(e.target.value)}/></label>:<><label>Collateral type<select value={assetType} onChange={e=>setAssetType(e.target.value)}><option value="CASH">Cash</option><option value="SECURITY">Security</option></select></label>{assetType==='CASH'?<label>Cash amount (₹)<input type="number" min="0.01" step="0.01" required value={cashAmount} onChange={e=>setCashAmount(e.target.value)}/></label>:<><label>Security<select value={securityId} required onChange={e=>setSecurityId(e.target.value)}><option value="">Select security</option>{data.securities.map(s=><option key={val(s,'id')} value={val(s,'id')}>{val(s,'symbol')} · {val(s,'exchange')}</option>)}</select></label><label>Quantity<input type="number" min="1" required value={securityQty} onChange={e=>setSecurityQty(e.target.value)}/></label></>}</>}<footer><button type="button" onClick={()=>setModal('')}>Cancel</button><button className="lw-primary" disabled={busy}>{busy?'Submitting…':'Submit'}</button></footer></form></div>}
 </main>
}

function CollateralTable({positions,securities,full=false}){
 const [page,setPage]=useState(1),[pageSize,setPageSize]=useState(25),pages=Math.max(1,Math.ceil(positions.length/pageSize)),visible=positions.slice((page-1)*pageSize,page*pageSize);
 useEffect(()=>setPage(1),[positions,pageSize]);useEffect(()=>{if(page>pages)setPage(pages)},[page,pages]);
 return <><div className="lw-table-wrap"><table className="lw-table"><thead><tr><th>ISIN / Security</th><th className="right">Qty</th><th className="right">Haircut</th><th className="right">Eligible value</th><th className="right">Market value</th>{full&&<th>Status</th>}</tr></thead><tbody>{visible.map((p,i)=><tr key={val(p,'id')||i}><td><b>{fmt(p,'security')}</b></td><td className="right">{fmt(p,'qty')}</td><td className="right">{fmt(p,'haircut')}</td><td className="right">{fmt(p,'eligible')}</td><td className="right">{fmt(p,'market')}</td>{full&&<td><Badge value={val(p,'status')||'POSTED'}/></td>}</tr>)}{!positions.length&&<tr><td colSpan={full?6:5} className="lw-empty">No collateral lots recorded.</td></tr>}</tbody>{positions.length>0&&<tfoot><tr><td colSpan={3}>Total collateral value</td><td className="right">{money(positions.reduce((sum,p)=>sum+Number(fmt(p,'rawEligible')||0),0))}</td><td className="right">{money(positions.reduce((sum,p)=>sum+Number(fmt(p,'rawMarket')||0),0))}</td>{full&&<td/>}</tr></tfoot>}</table></div><TablePagination page={page} pageSize={pageSize} total={positions.length} onPageChange={setPage} onPageSizeChange={setPageSize}/></>
}
function RecordTable({items,columns,fields}){
 const [page,setPage]=useState(1),[pageSize,setPageSize]=useState(25),pages=Math.max(1,Math.ceil(items.length/pageSize)),visible=items.slice((page-1)*pageSize,page*pageSize);
 useEffect(()=>setPage(1),[items,pageSize]);useEffect(()=>{if(page>pages)setPage(pages)},[page,pages]);
 const cell=(item,field,index)=>{const raw=val(item,field);if(index===fields.length-1)return <Badge value={raw||'RECORDED'}/>;if(raw==null)return '-';if(/date|at$|_at$|Time$/i.test(field))return stamp(raw);if(field==='id'||field==='period'||typeof raw==='string'&&!/^\\d+(\\.\\d+)?$/.test(raw))return String(raw);if(/date|at$|_at$/i.test(field))return date(raw);if(/amount/i.test(field))return money(raw);if(/rate/i.test(field))return bps(raw);return n(raw)};
 return <><div className="lw-table-wrap"><table className="lw-table"><thead><tr>{columns.map(c=><th key={c}>{c}</th>)}</tr></thead><tbody>{visible.map((item,i)=><tr key={val(item,'id')||i}>{fields.map((f,j)=><td key={f}>{cell(item,f,j)}</td>)}</tr>)}{!items.length&&<tr><td colSpan={columns.length} className="lw-empty">No records available.</td></tr>}</tbody></table></div><TablePagination page={page} pageSize={pageSize} total={items.length} onPageChange={setPage} onPageSizeChange={setPageSize}/></>
}
