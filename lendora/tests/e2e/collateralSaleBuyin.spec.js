import { test, expect } from '@playwright/test'
const reply=(route,data)=>route.fulfill({json:{success:true,data}})
async function setup(page,cashAfterSale=12000) {
  await page.route('http://localhost:8086/api/v1/auth/me',route=>reply(route,{organization:{type:'LENDER',name:'Fund',platformOrganizationId:37},user:{fullName:'Admin'}}))
  await page.route('http://localhost:8084/**',route=>reply(route,[]))
  const lot={positionId:8,securityId:7,symbol:'INFY',quantity:60,price:200,proceeds:cashAfterSale}
  const funding={saleScope:"review-1",needsSale:true,securityLots:[lot],cashHeld:0,estimatedProceeds:cashAfterSale,canSell:true,blockers:[],sales:[]}
  const row={default:{id:5,ownerReferenceId:9005,loanId:9,obligationId:8005,obligationType:'RECALL',status:'OPEN'},loan:{id:9,status:'ACTIVE',outstandingQuantity:100},security:{symbol:'TCS',price:100},borrowerName:'Borrower',recall:{quantity:100,fulfilledQuantity:0},reportedData:{quantity:100},requests:[{id:333,type:'BUY_IN_REQUESTED',status:'ACCEPTED'}],orders:[],buyIns:[],collateralFunding:funding}
  await page.route('**/organizations/37/lenddesk/defaults-buyins',route=>reply(route,[row]))
  await page.route('**/organizations/37/defaults/5/collateral-sale-preview',route=>reply(route,row.collateralFunding))
  const sales=[],orders=[]
  await page.route('**/organizations/37/defaults/5/sell-collateral',route=>{sales.push(route.request().postDataJSON());row.collateralFunding={...funding,needsSale:false,cashHeld:cashAfterSale,securityLots:[],sales:[{id:1,proceeds:cashAfterSale,reference:'SALE-1'}]};return reply(route,row.collateralFunding.sales[0])})
  await page.route('**/organizations/37/defaults/5/buy-in-orders',route=>{orders.push(route.request().postDataJSON());return reply(route,{id:10,status:'EXECUTION_PENDING'})})
  return {row,sales,orders}
}
test('securities collateral must be sold first and order submission is a separate action',async({page})=>{
  const state=await setup(page)
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.getByRole('button',{name:'Place buy-in order',exact:true})).toHaveCount(0)
  await page.getByRole('button',{name:'Sell securities collateral',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await expect(dialog).toContainText('INFY')
  await expect(dialog).toContainText('₹12,000')
  await dialog.getByRole('button',{name:'Confirm sale',exact:true}).click()
  await expect(dialog).toHaveCount(0)
  expect(state.sales).toHaveLength(1);expect(state.sales[0].saleScope).toBe("review-1");expect(state.orders).toHaveLength(0)
  await expect(page.getByText('Collateral sale complete',{exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Place buy-in order',exact:true}).click()
  await dialog.getByLabel('Additional execution fees (₹)',{exact:true}).fill('5')
  await dialog.getByRole('button',{name:'Place order',exact:true}).click()
  expect(state.orders).toHaveLength(1)
  expect(state.orders[0]).toMatchObject({quantity:100,type:'MARKET',costs:5})
})
test('insufficient sale proceeds cannot submit a buy-in order',async({page})=>{
  const state=await setup(page,5000)
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button',{name:'Sell securities collateral',exact:true}).click()
  await page.getByRole('dialog').getByRole('button',{name:'Confirm sale',exact:true}).click()
  await page.getByRole('button',{name:'Place buy-in order',exact:true}).click()
  await expect(page.getByRole('dialog')).toContainText('Additional cash collateral is required')
  await expect(page.getByRole('dialog').getByRole('button',{name:'Place order',exact:true})).toBeDisabled()
  expect(state.orders).toHaveLength(0)
})
