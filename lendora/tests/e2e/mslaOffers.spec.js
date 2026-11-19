import { test, expect } from '@playwright/test'
async function lender(page) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'LENDER', name: 'Lender fund', platformOrganizationId: 37 }, user: { fullName: 'Lender Admin' } } } }))
  await page.route('http://localhost:8084/**', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/organizations/37/lenddesk/agreements/overview?*', route => route.fulfill({ json: { success: true, data: { agreements: { content: [], totalPages: 1 }, borrowers: [{ connectionId: 5, name: 'Registered borrower', externalId: 'BORROWER_DEMO' }], statuses: [], activeAgreementCount: 0 } } }))
  await page.route('**/api/v1/securities', route => route.fulfill({ json: { success: true, data: [{ id: 7, symbol: 'TCS', name: 'Tata Consultancy Services' }, { id: 8, symbol: 'INFY', name: 'Infosys' }] } }))
}
test('MSLA offer accepts mixed cash/security rules and converts haircut percentages', async ({ page }) => {
  await lender(page)
  let body
  await page.route('**/organizations/37/agreements', route => { body = route.request().postDataJSON(); return route.fulfill({ json: { success: true, data: { id: 21, number: body.number, status: 'OFFERED' } } }) })
  await page.goto('http://localhost:5175/agreements')
  await page.getByRole('button', { name: 'Send MSLA offer', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByLabel('Collateral rule 1 haircut (%)')).toHaveValue('0')
  await expect(dialog.getByLabel('Collateral rule 1 haircut (%)')).toHaveAttribute('readonly', '')
  await dialog.getByRole('button', { name: '+ Add collateral rule', exact: true }).click()
  await dialog.getByLabel('Collateral rule 2 security').selectOption('7')
  await dialog.getByLabel('Collateral rule 2 haircut (%)').fill('15')
  await dialog.getByRole('button', { name: 'Send to borrower' }).click()
  await expect(dialog).toHaveCount(0)
  expect(body.collateralRules).toEqual([{ assetType: 'CASH', securityId: null, haircut: 0 }, { assetType: 'SECURITY', securityId: 7, haircut: 0.15 }])
  await expect(page.locator('.live-agreement-notice')).toContainText('queued for borrower delivery')
})
test('MSLA security-only rules do not silently include cash and duplicates are refused', async ({ page }) => {
  await lender(page)
  const bodies = []
  await page.route('**/organizations/37/agreements', route => { bodies.push(route.request().postDataJSON()); return route.fulfill({ json: { success: true, data: { id: 22, number: 'MSLA-SECURITY', status: 'OFFERED' } } }) })
  await page.goto('http://localhost:5175/agreements')
  await page.getByRole('button', { name: 'Send MSLA offer', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Remove collateral rule 1', exact: true }).click()
  await dialog.getByRole('button', { name: 'Send to borrower' }).click()
  await expect(dialog.getByRole('alert')).toContainText('at least one')
  await dialog.getByRole('button', { name: '+ Add collateral rule', exact: true }).click()
  await dialog.getByLabel('Collateral rule 1 security').selectOption('8')
  await dialog.getByLabel('Collateral rule 1 haircut (%)').fill('25')
  await dialog.getByRole('button', { name: '+ Add collateral rule', exact: true }).click()
  await dialog.getByLabel('Collateral rule 2 security').selectOption('8')
  await dialog.getByRole('button', { name: 'Send to borrower' }).click()
  await expect(dialog.getByRole('alert')).toContainText('only once')
  expect(bodies).toHaveLength(0)
  await dialog.getByRole('button', { name: 'Remove collateral rule 2', exact: true }).click()
  await dialog.getByRole('button', { name: 'Send to borrower' }).click()
  await expect(dialog).toHaveCount(0)
  expect(bodies[0].collateralRules).toEqual([{ assetType: 'SECURITY', securityId: 8, haircut: 0.25 }])
})
test('BorrowDesk proposed tab includes newly received OFFERED agreements on refresh', async ({ page }) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message))
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'BORROWER', name: 'Borrower fund', platformOrganizationId: 77 }, user: { fullName: 'Borrower Admin' } } } }))
  await page.route('http://localhost:8081/**', route => route.fulfill({ json: { success: true, data: [] } }))
  let received = false
  const offer = { id: 41, number: 'MSLA-RECEIVED', lenderId: 1000000000001, status: 'OFFERED', effectiveDate: '2026-11-18', expiryDate: '2027-11-18', creditLimit: 100000, feeRate: 0.03, coverageRatio: 1.05, collateralRules: [{ assetType: 'CASH', securityId: null, haircut: 0 }] }
  await page.route('**/organizations/77/agreements?*', route => route.fulfill({ json: { success: true, data: { content: received ? [offer] : [] } } }))
  await page.route('**/organizations/77/agreements/41', route => route.fulfill({ json: { success: true, data: offer } }))
  await page.goto('http://localhost:5173/agreements')
  await page.getByRole('button', { name: /^Proposed/ }).click()
  await expect(page.locator('.ag-table')).not.toContainText('MSLA-RECEIVED')
  received = true
  await expect(page.locator('.ag-table')).toContainText('MSLA-RECEIVED', { timeout: 10000 })
  await expect(page.locator('.ag-table')).toContainText('OFFERED')
  expect(errors).toEqual([])
})


test('unanswered MSLA offers can retry delivery without creating a new agreement', async ({ page }) => {
  await lender(page)
  await page.route('**/organizations/37/lenddesk/agreements/overview?*', route => route.fulfill({ json: { success: true, data: { agreements: { content: [{ id: 21, number: 'MSLA-STUCK', borrowerName: 'Registered borrower', status: 'OFFERED' }], totalPages: 1 }, borrowers: [], statuses: ['OFFERED'], activeAgreementCount: 0 } } }))
  const calls = []
  await page.route('**/organizations/37/agreements/21/resend', route => { calls.push(route.request().postDataJSON()); return route.fulfill({ json: { success: true, data: { agreementId: 21, status: 'QUEUED' } } }) })
  await page.goto('http://localhost:5175/agreements')
  await page.getByRole('button', { name: 'Retry delivery', exact: true }).click()
  await expect(page.locator('.live-agreement-notice')).toContainText('No new agreement was created')
  expect(calls).toEqual([{}])
})
