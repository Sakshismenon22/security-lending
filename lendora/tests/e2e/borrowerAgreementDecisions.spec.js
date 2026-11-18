import { test, expect } from '@playwright/test'

async function borrowerOffer(page, initialStatus = 'OFFERED') {
  let offer = { id: 41, number: 'MSLA-DECISION', lenderId: 10, status: initialStatus, effectiveDate: '2026-11-18', expiryDate: '2027-11-18', creditLimit: 100000, feeRate: 0.03, coverageRatio: 1.05, collateralRules: [{ assetType: 'SECURITY', securityId: 7, haircut: 0.15 }] }
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'BORROWER', name: 'Borrower fund', platformOrganizationId: 77 }, user: { fullName: 'Borrower Admin' } } } }))
  await page.route('http://localhost:8081/**', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/organizations/77/agreements?*', route => route.fulfill({ json: { success: true, data: { content: [offer] } } }))
  await page.route('**/organizations/77/agreements/41', route => route.fulfill({ json: { success: true, data: offer } }))
  return { setStatus: status => { offer = { ...offer, status }; return offer } }
}

test('borrower accepts from the register, prevents duplicate submissions and sees lender confirmation', async ({ page }) => {
  const offer = await borrowerOffer(page)
  const calls = []
  let finish
  const released = new Promise(resolve => { finish = resolve })
  await page.route('**/organizations/77/agreements/41/accept', async route => {
    calls.push(route.request().postDataJSON())
    await released
    await route.fulfill({ json: { success: true, data: offer.setStatus('ACCEPTANCE_PENDING') } })
  })
  await page.goto('http://localhost:5173/agreements')
  await page.getByRole('button', { name: 'Accept offer', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toContainText('eligible collateral and haircuts')
  await dialog.getByRole('button', { name: 'Confirm acceptance' }).click()
  await expect(dialog.getByRole('button', { name: 'Submitting…' })).toBeDisabled()
  await expect(dialog.getByRole('button', { name: 'Cancel', exact: true })).toBeDisabled()
  expect(calls).toEqual([{}])
  finish()
  await expect(dialog).toHaveCount(0)
  await expect(page.getByRole('status')).toContainText('Awaiting lender confirmation')
  await expect(page.locator('.ag-table')).toContainText('ACCEPTANCE PENDING')
  await expect(page.getByRole('button', { name: 'Accept offer', exact: true })).toHaveCount(0)
  offer.setStatus('ACTIVE')
  await expect(page.locator('.ag-table')).toContainText('ACTIVE', { timeout: 10000 })
})

test('borrower can cancel rejection and submit a reason from the full MSLA workspace', async ({ page }) => {
  const offer = await borrowerOffer(page)
  const calls = []
  await page.route('**/organizations/77/agreements/41/reject', route => {
    calls.push(route.request().postDataJSON())
    return route.fulfill({ json: { success: true, data: offer.setStatus('REJECTION_PENDING') } })
  })
  await page.goto('http://localhost:5173/agreements/41')
  await expect(page.locator('.ag-ws-table-wrap')).toContainText('15%')
  await page.getByRole('button', { name: 'Reject offer', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Cancel', exact: true }).click()
  expect(calls).toEqual([])
  await page.getByRole('button', { name: 'Reject offer', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('Rejection reason (optional)').fill('  Haircut is too high  ')
  await dialog.getByRole('button', { name: 'Confirm rejection' }).click()
  await expect(dialog).toHaveCount(0)
  expect(calls).toEqual([{ reason: 'Haircut is too high' }])
  await expect(page.locator('.ag-ws-heading')).toContainText('REJECTION PENDING')
  await expect(page.getByRole('button', { name: 'Reject offer', exact: true })).toHaveCount(0)
  offer.setStatus('REJECTED')
  await expect(page.locator('.ag-ws-heading')).toContainText('REJECTED', { timeout: 10000 })
})

test('decision failures stay visible and allow retry without pretending acceptance succeeded', async ({ page }) => {
  const offer = await borrowerOffer(page)
  let attempts = 0
  await page.route('**/organizations/77/agreements/41/accept', route => {
    attempts++
    return attempts === 1
      ? route.fulfill({ status: 409, json: { message: 'Please retry this decision' } })
      : route.fulfill({ json: { success: true, data: offer.setStatus('ACCEPTANCE_PENDING') } })
  })
  await page.goto('http://localhost:5173/agreements/41')
  await page.getByRole('button', { name: 'Accept offer', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: 'Confirm acceptance' }).click()
  await expect(dialog.getByRole('alert')).toHaveText('Please retry this decision')
  await expect(page.getByRole('status')).toHaveCount(0)
  await dialog.getByRole('button', { name: 'Confirm acceptance' }).click()
  await expect(dialog).toHaveCount(0)
  expect(attempts).toBe(2)
  await page.reload()
  await expect(page.locator('.ag-ws-heading')).toContainText('ACCEPTANCE PENDING')
  await expect(page.getByRole('button', { name: /^(Accept|Reject) offer$/ })).toHaveCount(0)
})

test('active agreements do not expose offer decisions', async ({ page }) => {
  await borrowerOffer(page, 'ACTIVE')
  await page.goto('http://localhost:5173/agreements')
  await expect(page.locator('.ag-table')).toContainText('ACTIVE')
  await expect(page.getByRole('button', { name: /^(Accept|Reject) offer$/ })).toHaveCount(0)
})
