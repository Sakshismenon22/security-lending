import { test, expect } from '@playwright/test'

function snapshot() {
  return [5, 6].map(id => ({
    default: { id, ownerReferenceId: 9000 + id, loanId: 9, obligationId: 8000 + id, obligationType: 'RECALL', reason: 'Recall overdue', status: 'OPEN', createdAt: '2026-11-18T06:30:00Z' },
    reportedData: { status: 'OPEN', obligationType: 'RECALL' },
    loan: { id: 9, outstandingQuantity: 100, securityId: 7 },
    borrowerName: 'Live borrower', security: { symbol: 'INFY' },
    recall: { quantity: 20, fulfilledQuantity: 5, dueAt: '2026-11-18T05:30:00Z' },
    marginCall: null, buyIns: [], requests: [],
  }))
}
async function session(page) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'LENDER', name: 'Live lender', platformOrganizationId: 37 }, user: { fullName: 'Live Admin' } } } }))
  await page.route('http://localhost:8084/**', route => route.fulfill({ json: { success: true, data: [] } }))
}
const rowPath = '**/api/v1/organizations/37/lenddesk/defaults-buyins'
const postPath = '**/api/v1/organizations/37/defaults/5/buy-in-requests'
const refresh = page => page.getByRole('button', { name: 'Refresh', exact: true }).click()

test('live buy-in submits real IDs and remains pending across reloads', async ({ page }) => {
  await session(page)
  const rows = snapshot(), bodies = []
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  await page.route(postPath, route => {
    bodies.push(route.request().postDataJSON())
    rows[0].requests = [{ id: 333, status: 'PENDING', createdAt: '2026-11-18T09:00:00Z' }]
    return route.fulfill({ json: { success: true, data: rows[0].requests[0] } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.locator('.db-preview-chip')).toHaveText('LIVE DATA')
  await expect(page.locator('.db-main-table')).toContainText('DEF-000005')
  await expect(page.locator('.db-main-table')).not.toContainText('DEF-000018')
  await expect(page.getByRole('button', { name: 'Record default', exact: true })).toHaveCount(0)
  await page.getByLabel('Deadline', { exact: true }).fill('2026-11-19')
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await expect(page.locator('.db-main-table')).toContainText('No cases match')
  await page.getByRole('button', { name: 'Reset', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Prepare notice', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Request buy-in', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('does not confirm execution or settlement')
  await expect(dialog.getByRole('spinbutton')).toHaveCount(0)
  await dialog.getByRole('button', { name: 'Submit buy-in request', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Buy-in pending', exact: true })).toBeDisabled()
  expect(bodies).toHaveLength(1)
  expect(Object.keys(bodies[0])).toEqual(['clientRequestId'])
  expect(bodies[0].clientRequestId).toMatch(/^lenddesk-buyin-/)
  await page.reload()
  await expect(page.getByRole('button', { name: 'Buy-in pending', exact: true })).toBeDisabled()
  await page.locator('.db-main-table').getByRole('button', { name: 'DEF-000006', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Request buy-in', exact: true }).first()).toBeEnabled()
})

test('failed requests expose the reason and retry uses a stable key after network errors', async ({ page }) => {
  await session(page)
  const rows = snapshot(); rows[0].requests = [{ id: 300, status: 'FAILED', failureReason: 'Kafka delivery exhausted', createdAt: '2026-11-18T08:00:00Z' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const keys = []
  await page.route(postPath, route => {
    keys.push(route.request().postDataJSON().clientRequestId)
    if (keys.length === 1) return route.fulfill({ status: 503, json: { detail: 'Lifecycle request temporarily unavailable' } })
    rows[0].requests.unshift({ id: 334, status: 'PENDING', createdAt: '2026-11-18T09:00:00Z' })
    return route.fulfill({ json: { success: true, data: rows[0].requests[0] } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.locator('.db-live-request')).toContainText('Kafka delivery exhausted')
  await page.getByRole('button', { name: 'Retry buy-in', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Submit buy-in request' }).click()
  await expect(dialog.getByRole('alert')).toContainText('temporarily unavailable')
  await dialog.getByRole('button', { name: 'Submit buy-in request' }).click()
  await expect(dialog).toHaveCount(0)
  expect(keys[0]).toBe(keys[1])
  await expect(page.getByRole('button', { name: 'Buy-in pending', exact: true })).toBeDisabled()
  rows[0].requests[0].status = 'COMPLETED'
  rows[0].buyIns = [{ id: 41, defaultReferenceId: 9005, quantity: 15, price: '102', costs: '3', collateralApplied: '1533', residualBalance: '0', status: 'COMPLETED', createdAt: '2026-11-18T09:10:00Z' }]
  await refresh(page)
  await expect(page.locator('.db-main-table')).toContainText('BI-000041')
  await expect(page.getByRole('button', { name: 'Request buy-in', exact: true })).toBeDisabled()
  await expect(page.getByRole('heading', { name: 'Owner-reported amounts' })).toBeVisible()
  await expect(page.getByText('Awaiting securities receipt', { exact: true })).toBeVisible()
  await expect(page.locator('.db-metrics > div').filter({ hasText: 'Buy-ins pending' })).toContainText('0')
  await expect(page.locator('.db-main-table')).toContainText('In default')
})

test('unavailable or empty live data never falls back to sample defaults', async ({ page }) => {
  await session(page)
  let unavailable = true
  await page.route(rowPath, route => route.fulfill(unavailable ? { status: 503, json: { detail: 'Database unavailable' } } : { json: { success: true, data: [] } }))
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.getByRole('alert')).toContainText('Database unavailable')
  await expect(page.locator('.db-main-table')).not.toContainText('DEF-000018')
  unavailable = false
  await page.getByRole('button', { name: 'Retry', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await expect(page.locator('.db-main-table')).toContainText('No cases match')
  await expect(page.getByRole('button', { name: 'All cases 0', exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Sample preview', exact: true }).click()
  await expect(page.locator('.db-preview-chip')).toHaveText('SAMPLE DATA')
  await expect(page.locator('.db-main-table')).toContainText('DEF-000018')
})

test('accepted live buy-in records an execution and displays the lifecycle report after refresh', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'ACCEPTED', createdAt: '2026-11-18T09:00:00Z' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const bodies = []
  await page.route('**/api/v1/organizations/37/defaults/5/buy-in-executions', route => {
    bodies.push(route.request().postDataJSON())
    rows[0].requests.unshift({ id: 334, type: 'BUY_IN_EXECUTION_REQUESTED', status: 'PENDING', createdAt: '2026-11-18T09:01:00Z' })
    return route.fulfill({ json: { success: true, data: rows[0].requests[0] } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button', { name: 'Record external execution', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('Executed quantity')).toHaveValue('15')
  await dialog.getByLabel('Execution price (₹)').fill('101')
  await dialog.getByLabel('Execution costs (₹)').fill('5')
  await dialog.getByLabel('Trade confirmation reference').fill('TRADE-123')
  await dialog.getByRole('button', { name: 'Submit execution report' }).click()
  await expect(dialog).toHaveCount(0)
  expect(bodies).toHaveLength(1)
  expect(bodies[0]).toMatchObject({ buyinRequestId: 333, quantity: 15, price: 101, costs: 5, executionReference: 'TRADE-123' })
  await expect(page.getByRole('button', { name: 'Record external execution', exact: true })).toHaveCount(0)
  rows[0].requests.forEach(item => { item.status = 'COMPLETED' })
  rows[0].buyIns = [{ id: 41, quantity: 15, price: 101, costs: 5, collateralApplied: 0, residualBalance: 1520, status: 'EXECUTED', executionReference: 'TRADE-123', createdAt: '2026-11-18T09:02:00Z' }]
  await refresh(page)
  await expect(page.getByRole('cell', { name: 'TRADE-123', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'EXECUTED', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('cell', { name: 'TRADE-123', exact: true })).toBeVisible()
})

test('margin defaults cannot request securities buy-ins and stale execution errors allow correction', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].default.obligationType = 'MARGIN_CALL'
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.getByRole('button', { name: 'Request buy-in', exact: true })).toBeDisabled()
  rows[0].default.obligationType = 'RECALL'
  rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'ACCEPTED', createdAt: '2026-11-18T09:00:00Z' }]
  await refresh(page)
  await page.getByRole('button', { name: 'Record external execution', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Execution price (₹)').fill('101')
  await dialog.getByLabel('Trade confirmation reference').fill('TRADE-123')
  await page.route('**/api/v1/organizations/37/defaults/5/buy-in-executions', route => route.fulfill({ status: 409, json: { detail: 'The obligation has changed; refresh this case' } }))
  await dialog.getByRole('button', { name: 'Submit execution report' }).click()
  await expect(dialog.getByRole('alert')).toContainText('obligation has changed')
  await expect(dialog.getByRole('button', { name: 'Submit execution report' })).toBeEnabled()
})

test('demo market buy-in order is persisted and received inventory appears after fill', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].security.price = 100
  rows[0].loan.outstandingQuantity = 15
  const extraExecutions = []
  await page.route('**/organizations/37/defaults/5/buy-in-executions', route => { extraExecutions.push(route.request().postDataJSON()); return route.fulfill({ json: { success: true, data: {} } }) })
  rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'ACCEPTED', createdAt: '2026-11-18T09:00:00Z' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const bodies = []
  await page.route('**/api/v1/organizations/37/defaults/5/buy-in-orders', route => {
    bodies.push(route.request().postDataJSON())
    rows[0].orders = [{ id: 10, type: 'MARKET', quantity: 15, executionPrice: 101, costs: 5, executionReference: 'DEMO-BUYIN-10', status: 'EXECUTION_PENDING' }]
    rows[0].requests.unshift({ id: 334, type: 'BUY_IN_EXECUTION_REQUESTED', status: 'PENDING' })
    return route.fulfill({ json: { success: true, data: rows[0].orders[0] } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  const placeBox=await page.getByRole('button',{name:'Place buy-in order',exact:true}).boundingBox(),recordBox=await page.getByRole('button',{name:'Record external execution',exact:true}).boundingBox()
  expect(Math.abs(placeBox.height-recordBox.height)).toBeLessThan(2)
  expect(Math.abs(placeBox.y-recordBox.y)<2 || (Math.abs(placeBox.x-recordBox.x)<2 && Math.abs(placeBox.width-recordBox.width)<2)).toBeTruthy()
  await page.getByRole('button', { name: 'Place buy-in order', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('No external broker order')
  await dialog.getByLabel('Additional execution fees (₹)', { exact: true }).fill('5')
  await expect(dialog.getByRole('definition').filter({hasText:'₹1,505'})).toBeVisible()
  await expect(dialog).toContainText('Execution fees are optional and may be ₹0')
  await dialog.getByRole('button', { name: 'Place demo order', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(bodies[0]).toMatchObject({ buyinRequestId: 333, type: 'MARKET', quantity: 15, limitPrice: null, costs: 5 })
  await expect(page.getByRole('region', { name: 'Buy-in orders' })).toContainText('EXECUTION_PENDING')
  await expect(page.getByRole('button', { name: 'Place buy-in order', exact: true })).toHaveCount(0)
  rows[0].orders[0].status = 'FILLED'
  rows[0].orders[0].filledAt = '2026-11-18T10:00:00Z'
  rows[0].requests.forEach(r => { r.status = 'COMPLETED' })
  rows[0].buyIns = [{ id: 41, quantity: 15, price: 101, costs: 5, collateralApplied: 0, residualBalance: 1520, status: 'EXECUTED', executionReference: 'DEMO-BUYIN-10', inventoryReference: 'DEMO-BUYIN-10', inventoryReceivedAt: '2026-11-18T10:00:00Z' }]
  await refresh(page)
  await expect(page.getByRole('region', { name: 'Buy-in orders' })).toContainText('FILLED')
  await expect(page.getByText('Inventory received · cash payable', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: /^Receive securities/ })).toHaveCount(0)
  await expect(page.getByRole('button',{name:'Record execution & close loan',exact:true})).toBeVisible()
  const preview={loanStatus:'ACTIVE',outstandingQuantity:15,receivedQuantity:15,purchaseCost:1520,cashCollateralHeld:1700,cashCollateralToReturn:180,eligible:true,blockers:[]}
  await page.route('**/organizations/37/defaults/5/loan-closeout-preview',route=>route.fulfill({json:{success:true,data:preview}}))
  const closures=[]
  await page.route('**/organizations/37/defaults/5/close-loan',route=>{closures.push(route.request().postDataJSON());rows[0].loan.status='CLOSED';rows[0].loan.outstandingQuantity=0;rows[0].default.status='RESOLVED';rows[0].closeout={purchaseCost:1520,cashCollateralApplied:1520,cashCollateralReturned:180,reference:closures[0].reference,createdAt:'2026-11-18T11:00:00Z'};return route.fulfill({json:{success:true,data:rows[0].closeout}})})
  await page.getByRole('button',{name:'Record execution & close loan',exact:true}).click()
  await expect(page.getByRole('dialog')).toContainText('does not place another order or create another trade')
  await page.getByRole('dialog').getByRole('button',{name:'Confirm loan closure',exact:true}).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(closures).toHaveLength(1);expect(bodies).toHaveLength(1);expect(extraExecutions).toHaveLength(0)
  await expect(page.getByText('Loan closed · buy-in settled',{exact:true})).toBeVisible()
  await expect(page.getByRole('button',{name:'View loan closure',exact:true})).toBeVisible()

})

test('unmatched limit order can be cancelled and an external receipt requires confirmation', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'ACCEPTED' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  await page.route('**/api/v1/organizations/37/defaults/5/buy-in-orders', route => {
    expect(route.request().postDataJSON()).toMatchObject({ type: 'LIMIT', limitPrice: 90 })
    rows[0].orders = [{ id: 10, type: 'LIMIT', quantity: 15, limitPrice: 90, status: 'PENDING', executionReference: 'DEMO-10' }]
    return route.fulfill({ json: { success: true, data: rows[0].orders[0] } })
  })
  await page.route('**/api/v1/organizations/37/buy-in-orders/10/cancel', route => {
    rows[0].orders[0].status = 'CANCELLED'
    return route.fulfill({ json: { success: true, data: rows[0].orders[0] } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button', { name: 'Place buy-in order', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await dialog.getByLabel('Order type').selectOption('LIMIT')
  await dialog.getByLabel('Limit price (₹)').fill('90')
  await dialog.getByRole('button', { name: 'Place demo order', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  await page.getByRole('button', { name: 'Cancel order #10', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Buy-in orders' })).toContainText('CANCELLED')
  await expect(page.getByRole('button', { name: 'Place buy-in order', exact: true })).toBeVisible()
  rows[0].requests[0].status = 'COMPLETED'
  rows[0].buyIns = [{ id: 41, quantity: 15, price: 100, costs: 0, status: 'EXECUTED', executionReference: 'TRADE-41' }]
  await refresh(page)
  const receipts = []
  await page.route('**/api/v1/organizations/37/buy-ins/41/inventory-receipts', route => {
    receipts.push(route.request().postDataJSON())
    rows[0].buyIns[0].inventoryReceivedAt = '2026-11-18T10:00:00Z'
    rows[0].buyIns[0].inventoryReference = 'buyin-receipt-41'
    return route.fulfill({ json: { success: true, data: rows[0].buyIns[0] } })
  })
  await page.getByRole('button', { name: /^Receive securities/ }).click()
  dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Cancel', exact: true }).click()
  expect(receipts).toEqual([])
  await page.getByRole('button', { name: /^Receive securities/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm receipt', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(receipts).toEqual([{ reference: 'buyin-receipt-41' }])
  await expect(page.getByRole('button', { name: /^Receive securities/ })).toHaveCount(0)
})

test('received buy-in can close the loan from the same page and preserves closure after reload', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].loan.status = 'ACTIVE'
  rows[0].buyIns = [{ id: 41, quantity: 100, price: 100, costs: 5, status: 'EXECUTED', inventoryReceivedAt: '2026-11-18T10:00:00Z', inventoryReference: 'receipt-41' }]
  rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'COMPLETED' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const preview = { loanId: 9, loanStatus: 'ACTIVE', outstandingQuantity: 100, receivedQuantity: 100, purchaseCost: 10005, cashCollateralHeld: 11000, cashCollateralToReturn: 995, eligible: true, blockers: [], closeout: null }
  await page.route('**/api/v1/organizations/37/defaults/5/loan-closeout-preview', route => route.fulfill({ json: { success: true, data: preview } }))
  const calls = []
  await page.route('**/api/v1/organizations/37/defaults/5/close-loan', route => {
    calls.push(route.request().postDataJSON())
    rows[0].loan.status = 'CLOSED'
    rows[0].loan.outstandingQuantity = 0
    rows[0].default.status = 'RESOLVED'
    preview.eligible = false
    preview.loanStatus = 'CLOSED'
    preview.closeout = { reference: calls[0].reference, createdAt: '2026-11-18T10:10:00Z' }
    return route.fulfill({ json: { success: true, data: preview.closeout } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button', { name: 'Close loan after buy-in', exact: true }).click()
  let dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('10,005')
  await expect(dialog).toContainText('995')
  await expect(dialog).toContainText('Inventory will not be credited again')
  await dialog.getByRole('button', { name: 'Confirm loan closure' }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls).toHaveLength(1)
  expect(calls[0].reference).toMatch(/^close-buyin-/)
  await expect(page.getByRole('button', { name: 'View loan closure', exact: true })).toBeVisible()
  await page.reload()
  await page.getByRole('button', { name: 'View loan closure', exact: true }).click()
  dialog = page.getByRole('dialog')
  await expect(dialog).toContainText(calls[0].reference)
  await expect(dialog.getByRole('button', { name: 'Confirm loan closure' })).toHaveCount(0)
})

test('closure preview blocks insufficient receipts and revalidates changed collateral', async ({ page }) => {
  await session(page)
  const rows = snapshot()
  rows[0].buyIns = [{ id: 41, quantity: 15, price: 100, costs: 5, status: 'EXECUTED', inventoryReceivedAt: '2026-11-18T10:00:00Z' }]
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const preview = { loanStatus: 'ACTIVE', outstandingQuantity: 100, receivedQuantity: 15, purchaseCost: 1505, cashCollateralHeld: 11000, cashCollateralToReturn: 9495, eligible: false, blockers: ['Received buy-in quantity must cover the full outstanding loan'] }
  await page.route('**/api/v1/organizations/37/defaults/5/loan-closeout-preview', route => route.fulfill({ json: { success: true, data: preview } }))
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button', { name: 'Close loan after buy-in', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('full outstanding loan')
  await expect(dialog.getByRole('button', { name: 'Confirm loan closure' })).toBeDisabled()
  preview.eligible = true; preview.blockers = []; preview.receivedQuantity = 100
  await dialog.getByRole('button', { name: 'Refresh checks' }).click()
  await expect(dialog.getByRole('button', { name: 'Confirm loan closure' })).toBeEnabled()
  await page.route('**/api/v1/organizations/37/defaults/5/close-loan', route => route.fulfill({ status: 409, json: { detail: 'Insufficient cash collateral to settle buy-in costs' } }))
  await dialog.getByRole('button', { name: 'Confirm loan closure' }).click()
  await expect(dialog.getByRole('alert')).toContainText('Insufficient cash collateral')
  await expect(dialog).toBeVisible()
})

test('manual default scan runs once, refreshes cases and removes the global top bar', async ({ page }) => {
  await session(page)
  let rows = []
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
  const calls = []
  let finish
  const gate = new Promise(resolve => { finish = resolve })
  await page.route('**/api/v1/organizations/37/lenddesk/defaults-buyins/scan', async route => {
    calls.push(route.request().postDataJSON())
    await gate
    rows = snapshot()
    await route.fulfill({ json: { success: true, data: { organizationId: 37, loansChecked: 3, openCases: 2, loansFailed: 0 } } })
  })
  await page.goto('http://localhost:5175/defaults-buyins')
  await expect(page.locator('.topbar')).toHaveCount(0)
  await expect(page.getByPlaceholder('Search loans, securities, borrowers, or IDs…')).toHaveCount(0)
  await page.getByRole('button', { name: 'Run default scan', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Scanning…', exact: true })).toBeDisabled()
  expect(calls).toEqual([{}])
  finish()
  await expect(page.getByRole('status')).toContainText('3 loans checked, 2 open cases')
  await expect(page.locator('.db-main-table')).toContainText('DEF-000005')
  await expect(page.getByRole('button', { name: 'Run default scan', exact: true })).toBeEnabled()
  await page.goto('http://localhost:5175/defaults-buyins/preview')
  await expect(page.locator('.topbar')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Run default scan', exact: true })).toHaveCount(0)
})

test('manual scan displays service errors and partial failures without claiming complete success', async ({ page }) => {
  await session(page)
  await page.route(rowPath, route => route.fulfill({ json: { success: true, data: [] } }))
  let fail = true
  await page.route('**/api/v1/organizations/37/lenddesk/defaults-buyins/scan', route => route.fulfill(fail ? { status: 503, json: { detail: 'Lifecycle scan temporarily unavailable' } } : { json: { success: true, data: { organizationId: 37, loansChecked: 4, loansFailed: 1, openCases: 2 } } }))
  await page.goto('http://localhost:5175/defaults-buyins')
  await page.getByRole('button', { name: 'Run default scan', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('temporarily unavailable')
  await expect(page.getByRole('button', { name: 'Run default scan', exact: true })).toBeEnabled()
  fail = false
  await page.getByRole('button', { name: 'Run default scan', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('1 loans could not be checked')
  await expect(page.getByRole('status')).toContainText('4 loans checked')
})

for (const viewport of [{ width: 1440, height: 1000 }, { width: 800, height: 900 }, { width: 390, height: 844 }]) {
  test(`buy-in estimate labels and currency amounts stay separated at ${viewport.width}px`, async ({ page }) => {
    await page.setViewportSize(viewport)
    await session(page)
    const rows = snapshot()
    rows[0].security.price = 1363
    rows[0].reportedData.quantity = 100
    rows[0].requests = [{ id: 333, type: 'BUY_IN_REQUESTED', status: 'ACCEPTED' }]
    await page.route(rowPath, route => route.fulfill({ json: { success: true, data: rows } }))
    await page.goto('http://localhost:5175/defaults-buyins')
    await page.getByRole('button', { name: 'Place buy-in order', exact: true }).click()
    const dialog = page.getByRole('dialog')
    const estimate = dialog.locator('.db-buyin-purchase-estimate')
    await expect(estimate.locator('dd').last()).toHaveText('₹1,36,300')
    const layout = await estimate.evaluate(element => {
      const dialog = element.closest('[role="dialog"]')
      return {
        estimateOverflow: element.scrollWidth - element.clientWidth,
        dialogOverflow: dialog.scrollWidth - dialog.clientWidth,
        bounds: { left: dialog.getBoundingClientRect().left, right: dialog.getBoundingClientRect().right },
        rows: [...element.children].map(row => {
          const label = row.querySelector('dt').getBoundingClientRect()
          const amount = row.querySelector('dd').getBoundingClientRect()
          const bounds = row.getBoundingClientRect()
          return { top: bounds.top, bottom: bounds.bottom, labelRight: label.right, amountLeft: amount.left, amountHeight: amount.height }
        }),
      }
    })
    expect(layout.rows).toHaveLength(4)
    expect(layout.estimateOverflow).toBeLessThanOrEqual(1)
    expect(layout.dialogOverflow).toBeLessThanOrEqual(1)
    expect(layout.bounds.left).toBeGreaterThanOrEqual(0)
    expect(layout.bounds.right).toBeLessThanOrEqual(viewport.width)
    for (let index = 0; index < layout.rows.length; index++) {
      const row = layout.rows[index]
      expect(row.amountLeft - row.labelRight).toBeGreaterThanOrEqual(10)
      expect(row.amountHeight).toBeLessThan(24)
      if (index) expect(row.top).toBeGreaterThanOrEqual(layout.rows[index - 1].bottom - 1)
    }
  })
}
