import{get,orgUrl}from'./apiClient';
import{unwrapRows}from'./apiClient';
const base=orgUrl('');
async function all(path) {
 const records=[];let page=0,result;
 do {result=await get(`${path}?page=${page++}&size=200`);records.push(...unwrapRows(result));} while(page<(result?.totalPages??1));
 return records;
}

const rows=async promise=>unwrapRows(await promise);
export const borrowerBlotterApi={
 async snapshot(){
  const names=['dashboard','loans','locates','reservations','agreements','lenders','collateral','marginCalls','recalls','returns','fees','rebates','securities','audit','trades'];
  const calls=[get(base+'/dashboard'),all(base+'/loans'),all(base+'/locates'),all(base+'/reservations'),all(base+'/agreements'),get(base+'/lenders'),all(base+'/collateral'),all(base+'/margin-calls'),all(base+'/recalls'),all(base+'/returns'),all(base+'/fees'),all(base+'/rebates'),get('/api/v1/securities'),all(base+'/audit'),all(base+'/trades')];
  const settled=await Promise.allSettled(calls),out={};
  settled.forEach((item,index)=>{out[names[index]]=item.status==='fulfilled'?(index===0?item.value:unwrapRows(item.value)):null});
  out.errors=settled.flatMap((item,index)=>item.status==='rejected'?[{resource:names[index],message:item.reason?.message||'Unavailable'}]:[]);
  return out;
 },
 loan:id=>get(base+'/loans/'+id),
 valuation:id=>get(base+'/loans/'+id+'/valuation')
};