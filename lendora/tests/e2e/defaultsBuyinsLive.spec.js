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
  await expect(page.getByText('Not supplied by this report', { exact: true })).toBeVisible()
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
