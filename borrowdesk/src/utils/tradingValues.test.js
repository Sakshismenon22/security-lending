import test from 'node:test';
import assert from 'node:assert/strict';
import { candleQuote, positionValues, tradingAliases } from './tradingValues.js';
const candles=[
 {tradedDateTimestamp:'2026-11-13T09:59:00Z',open:98,high:102,low:98,close:100,volume:100},
 {tradedDateTimestamp:'2026-11-16T03:45:00Z',open:101,high:104,low:100,close:103,volume:200},
 {tradedDateTimestamp:'2026-11-16T03:46:00Z',open:103,high:106,low:102,close:105,volume:300},
];
test('quotes use previous trading session close and latest session volume',()=>{
 const q=candleQuote([...candles].reverse());assert.equal(q.price,105);assert.equal(q.previousClose,100);assert.equal(q.change,5);assert.equal(q.changePercent,5);assert.equal(q.volume,500);
});
test('long and short day MTM have opposite signs',()=>{
 const q=candleQuote(candles);
 assert.equal(positionValues({longQuantity:10},q).dayPnl,50);
 const short=positionValues({shortQuantity:10},q);assert.equal(short.dayPnl,-50);assert.equal(short.dayPnlPercent,-5);
});
test('missing prior session remains unavailable instead of invented zero',()=>{
 const q=candleQuote(candles.slice(1));assert.equal(q.change,null);assert.equal(positionValues({longQuantity:10},q).dayPnl,null);
});
test('API camelCase fields remain available under existing snake_case lookups',()=>{
 assert.equal(tradingAliases({shortUnrealizedPnl:25,availableOwnedQuantity:10}).short_unrealized_pnl,25);
});
