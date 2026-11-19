import { test, expect } from '@playwright/test'
const alpha = { directoryId: 1000000000001, borrowerOrganizationId: 77, name: 'Alpha Securities', registrationNumber: 'ALPHA-REG', country: 'India', currency: 'INR', type: 'BORROWER' }
const beta = { ...alpha, directoryId: 1000000000002, borrowerOrganizationId: 78, name: 'Beta Capital', registrationNumber: 'BETA-REG' }
async function session(page) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'LENDER', name: 'Lender fund', platformOrganizationId: 37 }, user: { fullName: 'Lender Admin' } } } }))
  await page.route('http://localhost:8084/**', route => route.fulfill({ json: { success: true, data: [] } }))
}

test('lender selects a registered borrower without manually entering IDs', async ({ page }) => {
  await session(page)
  const connections = [], posts = []
  await page.route('**/organizations/37/lenddesk/borrowers?*', route => route.fulfill({ json: { success: true, data: { content: connections } } }))
  await page.route('**/organizations/37/borrower-directory', route => route.fulfill({ json: { success: true, data: { content: connections.length ? [beta] : [alpha, beta], totalRegistered: 2 } } }))
  await page.route('**/organizations/37/borrower-directory/connections', route => {
    posts.push(route.request().postDataJSON())
    connections.push({ id: 5, borrowerOrganizationId: 77, externalId: 'BORROWER_DEMO', name: alpha.name, active: true, createdAt: '2026-11-18T09:00:00Z' })
    return route.fulfill({ json: { success: true, data: connections[0] } })
  })
  await page.goto('http://localhost:5175/borrowers')
  await page.getByRole('button', { name: 'Add borrower', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('button', { name: 'Add borrower', exact: true })).toBeDisabled()
  await expect(dialog.getByLabel('Borrower external ID')).toHaveCount(0)
  await dialog.getByLabel('Search registered borrowers').fill('ALPHA-REG')
  await expect(dialog).toContainText('Alpha Securities')
  await expect(dialog.getByRole('radio', { name: 'Select Beta Capital' })).toHaveCount(0)
  await dialog.getByRole('radio', { name: 'Select Alpha Securities' }).check()
  await dialog.getByRole('button', { name: 'Add borrower', exact: true }).click()
  await expect(dialog).toHaveCount(0)
  expect(posts).toEqual([{ directoryId: alpha.directoryId }])
  await expect(page.getByRole('status')).toContainText('queued for delivery')
  await expect(page.locator('.borrower-reference-table')).toContainText('Alpha Securities')
  await page.getByRole('button', { name: 'Add borrower', exact: true }).click()
  await expect(dialog.getByRole('radio', { name: 'Select Alpha Securities' })).toHaveCount(0)
  await expect(dialog.getByRole('radio', { name: 'Select Beta Capital' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(dialog).toHaveCount(0)
})

test('directory errors can be retried and empty results explain registration', async ({ page }) => {
  await session(page)
  let unavailable = true
  await page.route('**/organizations/37/lenddesk/borrowers?*', route => route.fulfill({ json: { success: true, data: { content: [] } } }))
  await page.route('**/organizations/37/borrower-directory', route => route.fulfill(unavailable ? { status: 503, json: { detail: 'Account directory offline' } } : { json: { success: true, data: { content: [], totalRegistered: 0 } } }))
  await page.goto('http://localhost:5175/borrowers')
  await page.getByRole('button', { name: 'Add borrower', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('alert')).toContainText('Account directory offline')
  unavailable = false
  await dialog.getByRole('button', { name: 'Retry', exact: true }).click()
  await expect(dialog).toContainText('Borrowers need to complete Lendora signup first')
  await expect(dialog.getByRole('button', { name: 'Add borrower', exact: true })).toBeDisabled()
})

test('direct add borrower route uses the directory and preserves selection after rejected submission', async ({ page }) => {
  await session(page)
  let available = Array.from({ length: 10 }, (_, i) => ({ ...alpha, directoryId: alpha.directoryId + i, borrowerOrganizationId: 77 + i, name: `Registered borrower ${i + 1}`, registrationNumber: `REG-${i + 1}` }))
  await page.route('**/organizations/37/borrower-directory', route => route.fulfill({ json: { success: true, data: { content: available, totalRegistered: available.length } } }))
  await page.route('**/organizations/37/borrower-directory/connections', route => route.fulfill({ status: 404, json: { detail: 'Registered borrower is no longer available' } }))
  await page.goto('http://localhost:5175/borrowers/new')
  await expect(page.getByRole('heading', { name: 'Add registered borrower' })).toBeVisible()
  await page.getByRole('button', { name: 'Next', exact: true }).click()
  await page.getByRole('radio', { name: 'Select Registered borrower 10', exact: true }).check()
  await page.getByRole('button', { name: 'Add borrower', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('no longer available')
  expect(await page.getByRole('radio', { name: 'Select Registered borrower 10', exact: true }).isChecked()).toBe(true)
  available = []
  await page.getByRole('button', { name: 'Refresh', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Add borrower', exact: true })).toBeDisabled()
})
