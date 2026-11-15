import{get,post,orgUrl}from'./apiClient';
const b=orgUrl('');
export const billingApi={
 fees:()=>get(b+'/fees?page=0&size=200'),
 rebates:()=>get(b+'/rebates?page=0&size=200'),
 rebatePayments:()=>get(b+'/rebate-payments?page=0&size=200'),
 accruals:()=>get(b+'/accruals?page=0&size=200'),
 loanBilling:(id,r)=>get(b+'/loans/'+id+'/'+r+'?page=0&size=200'),
 payFee:(id,key)=>post(b+'/fees/'+id+'/payments',{}, {'Idempotency-Key':key})
};