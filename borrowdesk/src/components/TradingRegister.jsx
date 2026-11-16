import { useState } from 'react';
import { Minimize2, Maximize2 } from 'lucide-react';
const number = v => v == null ? '—' : Number(v).toLocaleString('en-IN', {maximumFractionDigits:2});
const money = v => v == null ? '—' : Number(v).toLocaleString('en-IN', {style:'currency',currency:'INR'});
const percentage = v => v == null ? '—' : `${Number(v)>0?'+':''}${Number(v).toFixed(2)}%`;
const time = v => v ? new Date(v).toLocaleString('en-IN',{timeZone:'Asia/Kolkata',day:'2-digit',month:'short',hour:'2-digit',minute:'2-digit',hour12:false}) : '—';
const id = v => String(v ?? '');
const pnlClass = value => value == null || !Number.isFinite(Number(value)) || Number(value) === 0 ? 'pnl-neutral' : Number(value) > 0 ? 'pnl-profit' : 'pnl-loss';
const byId = (rows,value) => rows.find(r => id(r.id) === id(value));
 
export default function TradingRegister({tab,onTab,orders,trades,positions,loans,collateral,securities,agreements=[],lenders=[]}) {
  const [filter,setFilter]=useState('All');
  const [minimized,setMinimized]=useState(false);
  const symbol = row => row.symbol ?? byId(securities,row.securityId ?? row.security_id)?.symbol ?? '—';
  const loanContext = loan => {
    const agreement=byId(agreements,loan?.mslaId);
    return byId(lenders,agreement?.lenderId)?.name ?? '—';
  };
  const positionRows=positions.flatMap(p=>{
    const long=Number(p.long_quantity ?? 0),short=Number(p.short_quantity ?? 0);
    const make=(quantity,source,average)=>{
      const previous=p.previousClose, price=p.price;
      const day=price!=null&&previous!=null ? quantity*(price-previous) : null;
      return {...p,key:`${p.security_id ?? p.securityId}:${source}`,quantity,source,average,
        marketValue:price!=null?quantity*price:null,day,dayPercent:day!=null&&previous&&quantity?day/(Math.abs(quantity)*previous)*100:null,
        unrealized:price!=null&&average!=null?quantity*(price-average):null};
    };
    return [...(long?[make(long,'Owned',p.average_cost)]:[]),...(short?[make(-short,'Borrowed',p.average_entry)]:[]),...(!long&&!short?[make(0,'Flat',null)]:[])];
  });
  const openPositions=positionRows.filter(p=>p.quantity!==0);
  const dayBase=openPositions.reduce((sum,p)=>sum+Math.abs(p.quantity)*Number(p.previousClose??0),0);
  const dayTotal=openPositions.every(p=>p.day!=null)?openPositions.reduce((sum,p)=>sum+p.day,0):null;
  const tabs=['Orders','Trades','Positions','Open Positions','Borrow & collateral'];
  const isPosition=tab==='Positions'||tab==='Open Positions';
  const counts={Orders:orders.length,Trades:trades.length,Positions:positionRows.length,'Open Positions':openPositions.length};
  let columns,records;
  const col=(key,label,type='text')=>({key,label,type});
  if(tab==='Orders') {
    columns=[col('id','Order ID'),col('createdAt','Time','time'),col('symbol','Security'),col('side','Side'),col('type','Type'),col('quantity','Qty','number'),col('limitPrice','Limit price','money'),col('executedPrice','Avg. fill price','money'),col('filled','Filled qty','number'),col('status','Status','status')];
    records=orders.map(order=>{
      const fills=trades.filter(t=>id(t.orderId)===id(order.id));
      const filled=fills.reduce((sum,t)=>sum+Number(t.quantity),0);
      return {...order,key:`order:${order.id}`,symbol:symbol(order),filled:filled || (order.status==='FILLED'?Number(order.quantity):0),executedPrice:filled?fills.reduce((sum,t)=>sum+Number(t.price)*Number(t.quantity),0)/filled:null};
    });
  } else if(tab==='Trades') {
    columns=[col('id','Trade ID'),col('orderId','Order ID'),col('executedAt','Time','time'),col('symbol','Security'),col('side','Side'),col('source','Source'),col('quantity','Qty','number'),col('price','Price','money'),col('value','Value','money')];
    records=trades.map(t=>({...t,key:`trade:${t.id}`,symbol:symbol(t),value:t.value ?? (t.price!=null&&t.quantity!=null?Number(t.price)*Number(t.quantity):null)}));
  } else if(isPosition) {
    columns=[col('symbol','Security'),col('quantity','Net qty','number'),col('average','Avg. entry','money'),col('price','LTP','money'),col('marketValue','Net market value','money'),col('day','Day MTM P&L','money'),col('dayPercent','Day MTM %','percent'),col('unrealized','Unrealized P&L','money'),col('source','Source')];
    records=(tab==='Open Positions'?openPositions:positionRows).map(p=>({...p,symbol:symbol(p)}));
  } else {
    columns=[col('kind','Type'),col('id','Reference'),col('loanId','Loan ID'),col('symbol','Security'),col('lender','Lender'),col('assetType','Asset'),col('quantity','Qty','number'),col('outstandingQuantity','Outstanding qty','number'),col('cashAmount','Cash collateral','money'),col('status','Status','status')];
    records=[...loans.map(l=>({...l,key:`loan:${l.id}`,kind:'Loan',loanId:l.id,symbol:symbol(l),lender:loanContext(l),assetType:'Borrowed security'})),...collateral.map(c=>{const loan=byId(loans,c.loanId);return {...c,key:`collateral:${c.id}`,kind:'Collateral',symbol:c.assetType==='CASH'?'Cash':symbol(c),lender:loanContext(loan)};})];
  }
  const options=tab==='Orders'?['All','PENDING','FILLED','CANCELLED','REJECTED']:tab==='Borrow & collateral'?['All','Loan','Collateral']:['All'];
  const visible=records.filter(r=>filter==='All'||!options.includes(filter)||(tab==='Orders'?r.status:r.kind)===filter);
  const format=(r,c)=>{
    const value=r[c.key];
    if(value==null||value==='')return tab==='Orders'&&c.key==='limitPrice'&&r.type==='MARKET'?'Market':'—';
    if(c.type==='money')return money(value);
    if(c.type==='number')return number(value);
    if(c.type==='percent')return percentage(value);
    if(c.type==='time')return time(value);
    return String(value);
  };
  return <section className={`trading-bottom-panel${minimized?' is-minimized':''}`}>
    <div className="bottom-left-panel">
      <div className="bottom-panel-top">
        <div className="bottom-tabs" role="tablist" aria-label="Trading registers">{tabs.map(name=><button key={name} role="tab" aria-selected={tab===name} className={tab===name?'active':''} onClick={()=>{setFilter('All');onTab(name)}}>{name}{counts[name]!=null?` (${counts[name]})`:''}</button>)}</div>
        <button className="register-size-toggle" type="button" onClick={()=>setMinimized(value=>!value)} aria-expanded={!minimized} aria-label={minimized?'Maximize trading table':'Minimize trading table'} title={minimized?'Maximize table':'Minimize table'}>
          {minimized?<Maximize2 size={13}/>:<Minimize2 size={13}/>}<span>{minimized?'Maximize':'Minimize'}</span>
        </button>
      </div>
      <div className="bottom-filter-row">{options.map(value=><button key={value} className={filter===value?'chosen':''} onClick={()=>setFilter(value)}>{value}</button>)}{isPosition&&<span className="register-pnl-note">Day MTM: current quantity × move from previous session close; excludes realized intraday P&amp;L.</span>}</div>
      <div className="bottom-table-wrap"><table className="bottom-trading-table trading-register"><thead><tr>{columns.map(c=><th key={c.key} className={['money','number','percent'].includes(c.type)?'num':undefined}>{c.label}</th>)}</tr></thead><tbody>{visible.map(r=><tr key={r.key}>{columns.map(c=><td key={c.key} className={[[ 'money','number','percent'].includes(c.type)?'num':'',c.key.startsWith('day')||c.key==='unrealized'?pnlClass(r[c.key]):''].join(' ')}>{c.type==='status'?<span className={'order-status '+String(r[c.key]??'').toLowerCase()}>{format(r,c)}</span>:c.key==='side'?<span className={'trade-side '+(String(r[c.key]).toUpperCase()==='BUY'?'side-buy':String(r[c.key]).toUpperCase()==='SELL'?'side-sell':'')}>{format(r,c)}</span>:format(r,c)}</td>)}</tr>)}{!visible.length&&<tr><td colSpan={columns.length} className="trade-empty">No {tab.toLowerCase()} records.</td></tr>}</tbody></table></div>
      {isPosition&&visible.length>0&&<div className="register-position-total">Open legs: {openPositions.length} · Day MTM P&amp;L: <b className={pnlClass(dayTotal)}>{visible.every(r=>r.day!=null)?money(visible.reduce((sum,r)=>sum+r.day,0)):'—'}</b> · Day MTM %: <b className={pnlClass(dayTotal)}>{dayTotal!=null&&dayBase?percentage(dayTotal/dayBase*100):'—'}</b></div>}
    </div>
  </section>;
}
