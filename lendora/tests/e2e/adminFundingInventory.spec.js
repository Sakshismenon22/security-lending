import { test, expect } from '@playwright/test'
async function session(page, type, admin) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type, name: 'Admin test firm', platformOrganizationId: 77 }, user: { fullName: 'Workspace user', email: 'member@example.com', role: 'ADMIN' } } } }))
  await page.route(`http://localhost:${type === 'BORROWER' ? 8081 : 8084}/**`, route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/organizations/77/admin-access', route => route.fulfill({ json: { success: true, data: { admin, role: admin ? 'ADMIN' : 'READ_ONLY', userId: 99 } } }))
}

test('borrower admin reviews paged deposits and accepts without supplying an approver ID', async ({ page }) => {
  await session(page, 'BORROWER', true)
  let approved = false
  await page.route('**/organizations/77/deposits?*', route => route.fulfill({ json: { success: true, data: { content: [{ id: 12, type: 'DEPOSIT', amount: 2500, status: approved ? 'CONFIRMED' : 'PENDING', clientRequestId: 'BANK-12' }], totalPages: 1 } } }))
  const calls = []
  await page.route('**/organizations/77/deposits/12/approve', route => { calls.push(route.request().postDataJSON()); approved = true; return route.fulfill({ json: { success: true, data: { id: 12, status: 'CONFIRMED' } } }) })
  await page.goto('http://localhost:5173/funding')
  await page.getByRole('button', { name: 'Deposit requests', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('BANK-12')
  await dialog.getByRole('button', { name: 'Accept deposit #12', exact: true }).click()
  await expect(dialog).toContainText('credits the borrower’s available cash immediately')
  await dialog.getByRole('button', { name: 'Confirm deposit acceptance', exact: true }).click()
  await expect(dialog).toContainText('CONFIRMED')
  await expect(dialog.getByRole('button', { name: 'Accept deposit #12', exact: true })).toHaveCount(0)
  expect(calls).toEqual([{}])
})

test('non-admin borrower cannot see approval controls even when account identity claims ADMIN', async ({ page }) => {
  await session(page, 'BORROWER', false)
  await page.goto('http://localhost:5173/funding')
  await expect(page.getByRole('heading', { name: 'Funding & account', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Deposit requests', exact: true })).toHaveCount(0)
})

test('lender admin records securities receipt and preserves the reference when retrying an error', async ({ page }) => {
  await session(page, 'LENDER', true)
  await page.route('**/api/v1/securities', route => route.fulfill({ json: { success: true, data: [{ id: 7, symbol: 'TCS', name: 'Tata Consultancy Services' }] } }))
  const rows = []
  await page.route('**/organizations/77/lenddesk/inventory?*', route => route.fulfill({ json: { success: true, data: { content: rows, totalPages: 1 } } }))
  const calls = []
  await page.route('**/organizations/77/inventory/receipts', route => {
    calls.push(route.request().postDataJSON())
    if (calls.length === 1) return route.fulfill({ status: 503, json: { detail: 'Inventory temporarily unavailable' } })
    rows.push({ id: 9, securityId: 7, symbol: 'TCS', name: 'Tata Consultancy Services', totalQuantity: 100, availableQuantity: 100, reservedQuantity: 0, loanedQuantity: 0 })
    return route.fulfill({ json: { success: true, data: rows[0] } })
  })
  await page.goto('http://localhost:5175/inventory')
  await page.getByRole('button', { name: 'Add securities', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('combobox', { name: 'Security', exact: true }).selectOption('7')
  await dialog.getByLabel('Quantity received', { exact: true }).fill('100')
  await dialog.getByLabel('Custody receipt reference', { exact: true }).fill('CUSTODY-TEST-1')
  await dialog.getByRole('button', { name: 'Confirm securities received', exact: true }).click()
  await expect(dialog.getByRole('alert')).toContainText('temporarily unavailable')
  await dialog.getByRole('button', { name: 'Confirm securities received', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls).toEqual([{ securityId: 7, quantity: 100, reference: 'CUSTODY-TEST-1' }, { securityId: 7, quantity: 100, reference: 'CUSTODY-TEST-1' }])
  await expect(page.locator('.ldi-main-table')).toContainText('TCS')
})

test('non-admin lender cannot see inventory receipt controls', async ({ page }) => {
  await session(page, 'LENDER', false)
  await page.goto('http://localhost:5175/inventory')
  await expect(page.getByRole('heading', { name: 'Securities Inventory', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Add securities', exact: true })).toHaveCount(0)
})
