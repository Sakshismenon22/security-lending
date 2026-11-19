import test from 'node:test';
import assert from 'node:assert/strict';
import { buildFeeRecords, relatedFeeHistory, feeRecordPath } from './feeRecords.js';
const data={loans:[{id:1,mslaId:2,securityId:3}],agreements:[{id:2,lenderId:4,dayBasis:365}],securities:[{id:3,symbol:'INFY'}],lenders:[{id:4,name:'Lender A'}],payments:[],
 accruals:[{id:1,loanId:1,accrualDate:'2026-11-16',type:'FEE',rate:.03,baseValue:10000,amount:.82},{id:2,loanId:1,accrualDate:'2026-11-15',type:'FEE',rate:.02,baseValue:9000,amount:.49}],
 fees:[{id:1,loanId:1,periodStart:'2026-11-16',periodEnd:'2026-11-16',feeAmount:.82,feeRate:.03,dueDate:'2026-11-17',status:'DUE'}],rebates:[]};
test('daily accruals and bills retain separate stable identities and direct paths',()=>{
 const rows=buildFeeRecords(data);assert.equal(rows.length,3);assert.equal(new Set(rows.map(feeRecordPath)).size,3);
 assert(rows.some(r=>feeRecordPath(r)==='/fees-rebates/accruals/1'));assert(rows.some(r=>feeRecordPath(r)==='/fees-rebates/fees/1'));
});
test('historical rate/base and recorded amount survive enrichment',()=>{
 const r=buildFeeRecords(data).find(r=>r.key==='accruals:2');assert.equal(r.baseValue,9000);assert.equal(r.rate,.02);assert.equal(r.amount,.49);assert.equal(r.dayBasis,365);assert.equal(r.lender,'Lender A');assert.equal(r.symbol,'INFY');
});
test('a daily record only links bills that actually cover that date',()=>{
 const rows=buildFeeRecords(data);assert.equal(rows.find(r=>r.key==='accruals:1').bill.id,1);assert.equal(rows.find(r=>r.key==='accruals:2').bill,undefined);
});
test('bill history excludes days outside its period',()=>{assert.deepEqual(relatedFeeHistory(buildFeeRecords(data).find(r=>r.key==='fees:1'),data).map(r=>r.id),[1]);});

test('paid fee and rebate settlements update their daily rows without treating closure as payment',()=>{
 const input={...data,loans:[{...data.loans[0],status:'CLOSED'}],rebates:[{id:4,loanId:1,periodStart:'2026-11-16',periodEnd:'2026-11-16',amount:1,status:'ACCRUED'}],accruals:[...data.accruals,{id:3,loanId:1,accrualDate:'2026-11-16',type:'REBATE',amount:1}]};
 let rows=buildFeeRecords(input);
 assert.equal(rows.find(r=>r.key==='accruals:1').status,'DUE');
 assert.equal(rows.find(r=>r.key==='accruals:3').status,'ACCRUED');
 input.fees=input.fees.map(f=>({...f,status:'PAID'}));input.rebates=input.rebates.map(r=>({...r,status:'PAID'}));
 rows=buildFeeRecords(input);
 for(const key of ['fees:1','accruals:1','rebates:4','accruals:3'])assert.equal(rows.find(r=>r.key===key).status,'PAID');
 assert.equal(rows.find(r=>r.key==='accruals:2').status,'RECORDED');
});

test('legacy one-day rebates without period fields link only the matching day',()=>{
 const input={...data,rebates:[{id:4,loanId:1,accrualDate:'2026-11-16',amount:1,status:'PAID'}],accruals:[{id:5,loanId:1,type:'REBATE',accrualDate:'2026-11-16',amount:1},{id:6,loanId:1,type:'REBATE',accrualDate:'2026-11-15',amount:1}]};
 const rows=buildFeeRecords(input);
 assert.equal(rows.find(r=>r.key==='accruals:5').status,'PAID');
 assert.equal(rows.find(r=>r.key==='accruals:6').status,'RECORDED');
});
