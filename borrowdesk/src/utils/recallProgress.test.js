import test from 'node:test';
import assert from 'node:assert/strict';
import { recallProgress } from './recallProgress.js';
test('failed returns keep recall capacity reserved until their assets are released',()=>{
 const recall={id:1,quantity:100,fulfilledQuantity:10};
 const pending=[{id:1,recallId:1,quantity:20,status:'FAILED',assetsReleased:false},{id:2,recallId:1,quantity:10,status:'PENDING'}];
 assert.equal(recallProgress(recall,pending).availableToRespond,60);
 pending[0].assetsReleased=true;
 assert.equal(recallProgress(recall,pending).availableToRespond,80);
});
