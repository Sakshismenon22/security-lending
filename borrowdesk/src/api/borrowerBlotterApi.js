import{get,orgUrl}from'./apiClient';
import{unwrapRows}from'./apiClient';
const base=orgUrl('');
const rows=async promise=>unwrapRows(await promise);
export const borrowerBlotterApi={
 async snapshot(){
  const names=['dashboard','loans','locates','reservations','agreements','lenders','collateral','marginCalls','recalls','returns','fees','rebates','securities','audit','trades'];
  const calls=[get(base+'/dashboard'),get(base+'/loans?page=0&size=200'),get(base+'/locates?page=0&size=200'),get(base+'/reservations?page=0&size=200'),get(base+'/agreements?page=0&size=200'),get(base+'/lenders'),get(base+'/collateral?page=0&size=200'),get(base+'/margin-calls?page=0&size=200'),get(base+'/recalls?page=0&size=200'),get(base+'/returns?page=0&size=200'),get(base+'/fees?page=0&size=200'),get(base+'/rebates?page=0&size=200'),get('/api/v1/securities'),get(base+'/audit?page=0&size=200'),get(base+'/trades?page=0&size=200')];
  const settled=await Promise.allSettled(calls),out={};
  settled.forEach((item,index)=>{out[names[index]]=item.status==='fulfilled'?(index===0?item.value:unwrapRows(item.value)):null});
  out.errors=settled.flatMap((item,index)=>item.status==='rejected'?[{resource:names[index],message:item.reason?.message||'Unavailable'}]:[]);
  return out;
 },
 loan:id=>get(base+'/loans/'+id),
 valuation:id=>get(base+'/loans/'+id+'/valuation')
};