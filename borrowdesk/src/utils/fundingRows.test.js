import test from 'node:test';
import assert from 'node:assert/strict';
import { fundingRows, isCredit, isDebit, cashTotal } from './fundingRows.js';
const context = {
 loans:[{id:10,mslaId:20,securityId:30}], agreements:[{id:20,lenderId:40}],
 lenders:[{id:40,name:'Lender A'}],securities:[{id:30,symbol:'INFY'}],
 collateral:[{id:50,loanId:10}],fees:[{id:60,loanId:10}],
 payments:[{id:70,feeId:60,status:'PENDING',type:'FEE'}],
 rebates:[{id:80,loanId:10}],orders:[{id:90,securityId:30}],
};
test('signed movements classify trades, blocks, refunds and rebates without guessing names',()=>{
 const rows=[{type:'TRADE_BUY',amount:-100},{type:'PAYMENT_CASH_BLOCK',amount:-20},{type:'PAYMENT_REFUNDED',amount:20},{type:'REBATE_RECEIVED',amount:5}];
 assert.equal(cashTotal(rows,isDebit),120);assert.equal(cashTotal(rows,isCredit),25);
 assert.equal(isDebit(rows[2]),false);assert.equal(isCredit({amount:0}),false);
});
test('joins reference types to the correct loan, payment, collateral or order',()=>{
 for(const [type,reference] of [['COLLATERAL_PLEDGED',10],['COLLATERAL_RELEASED',50],['PAYMENT_CASH_BLOCK',70],['REBATE_RECEIVED',80]]) {
  const [row]=fundingRows([{type,reference:String(reference),amount:-1}],context);
  assert.equal(row.lenderName,'Lender A');assert.equal(row.securitySymbol,'INFY');
 }
 const [trade]=fundingRows([{type:'TRADE_SELL',reference:'90',amount:10}],context);
 assert.equal(trade.securitySymbol,'INFY');assert.equal(trade.lenderName,null);
});
test('unrelated numeric payment IDs never match a ledger transaction ID',()=>{
 const [row]=fundingRows([{id:70,type:'ORDER_CASH_BLOCK',reference:'new-order',amount:-10}],context);
 assert.equal(row.paymentStatus,null);assert.equal(row.lenderName,null);
});
test('posted movement status stays separate from pending payment status',()=>{
 const [row]=fundingRows([{type:'PAYMENT_CASH_BLOCK',reference:'70',amount:-10}],context);
 assert.equal(row.status,'POSTED');assert.equal(row.paymentStatus,'PENDING');
});
