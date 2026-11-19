import { test, expect } from '@playwright/test'

async function lender(page, rows) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'LENDER', name: 'Live lender', platformOrganizationId: 37 }, user: { fullName: 'Admin' } } } }))
  await page.route('http://localhost:8084/**', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/organizations/37/lenddesk/margin-calls/overview?*', route => route.fulfill({ json: { success: true, data: { content: rows } } }))
}
const call = () => ({ id: 5, ownerReferenceId: 9, loanId: 9, borrowerName: 'Borrower firm', securitySymbol: 'INFY', amount: 300, fulfilledAmount: 0, remainingAmount: 300, dueAt: '2026-11-20T10:00:00Z', status: 'OPEN', reason: 'Coverage shortfall', canCancel: true })

test('lender confirms cancellation and sees borrower verification and final status', async ({ page }) => {
  const rows = [call()], submissions = []
  await lender(page, rows)
  await page.route('**/organizations/37/margin-calls/5/cancel', route => {
    submissions.push(route.request().postDataJSON())
    rows[0].status = 'CANCELLATION_PENDING'; rows[0].canCancel = false
    return route.fulfill({ json: { success: true, data: rows[0] } })
  })
  await page.goto('http://localhost:5175/margin-calls')
  await page.getByRole('button', { name: 'Cancel margin call', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('including any response still in transit')
  await dialog.getByRole('button', { name: 'Keep margin call' }).click()
  expect(submissions).toHaveLength(0)
  await page.getByRole('button', { name: 'Cancel margin call', exact: true }).click()
  await dialog.getByRole('button', { name: 'Confirm cancellation' }).click()
  await expect(dialog).toHaveCount(0)
  expect(submissions).toHaveLength(1)
  expect(submissions[0].reference).toMatch(/^cancel-margin-/)
  await expect(page.getByRole('table').first()).toContainText('Cancellation pending')
  await expect(page.getByRole('button', { name: 'Cancel margin call', exact: true })).toHaveCount(0)
  rows[0].status = 'CANCELLED'
  await page.getByRole('button', { name: 'Refresh live data' }).click()
  await expect(page.getByRole('table').first()).toContainText('Cancelled')
})

test('submitted collateral blocks cancellation and errors remain visible for correction', async ({ page }) => {
  const rows = [call(), { ...call(), id: 6, status: 'PARTIALLY_FULFILLED', fulfilledAmount: 100, canCancel: false }]
  await lender(page, rows)
  await page.route('**/organizations/37/margin-calls/5/cancel', route => route.fulfill({ status: 409, json: { detail: 'Collateral has already been submitted for this margin call' } }))
  await page.goto('http://localhost:5175/margin-calls')
  await expect(page.getByRole('button', { name: 'Cancel margin call', exact: true })).toHaveCount(1)
  await page.getByRole('button', { name: 'Cancel margin call', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Confirm cancellation' }).click()
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Collateral has already been submitted')
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Confirm cancellation' })).toBeEnabled()
})

test('BorrowDesk refreshes margin cancellation and disables collateral responses', async ({ page }) => {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'BORROWER', name: 'Borrower firm', platformOrganizationId: 77 }, user: { fullName: 'Admin' } } } }))
  await page.route('http://localhost:8081/**', route => route.fulfill({ json: { success: true, data: [] } }))
  const rows = [{ id: 9, loan_id: 41, external_id: 'AUTO-MARGIN-9', amount: 300, fulfilled_amount: 0, reason: 'Coverage shortfall', status: 'OPEN', request_time: '2026-11-19T10:00:00Z', due_at: '2026-11-20T10:00:00Z' }]
  await page.route('**/organizations/77/margin-calls', route => route.fulfill({ json: { success: true, data: rows } }))
  await page.goto('http://localhost:5173/margin-calls')
  await expect(page.getByRole('button', { name: 'Respond to call', exact: true })).toBeEnabled()
  rows[0].status = 'CANCELLED'
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(page.getByRole('button', { name: 'Respond to call', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Post collateral', exact: true })).toBeDisabled()
  await expect(page.locator('.mc-table')).toContainText('CANCELLED')
})
