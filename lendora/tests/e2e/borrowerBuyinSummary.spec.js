import { test, expect } from '@playwright/test'

const summary = { quantity: 100, averagePrice: 100, purchaseValue: 10000, executionCosts: 5, purchaseCost: 10005, cashCollateralApplied: 10005, cashCollateralReturned: 995, reference: 'CLOSE-BUYIN-42', closedAt: '2026-11-18T10:00:00Z' }
async function session(page, detail) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success: true, data: { organization: { type: 'BORROWER', name: 'Borrower fund', platformOrganizationId: 77 }, user: { fullName: 'Borrower Admin' } } } }))
  await page.route('http://localhost:8081/**', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/organizations/77/loans/41', route => route.fulfill({ json: { success: true, data: detail } }))
}
function loan() {
  return { id: 41, status: 'CLOSED', quantity: 100, outstanding_quantity: 0, fee_rate: 0.03, rebate_rate: 0.01, buyinClosure: { ...summary }, buyinClosurePending: false, lending_fee: [{ id: 1, loan_id: 41, fee_amount: 100, status: 'DUE' }], rebate: [{ id: 2, loan_id: 41, amount: 15, status: 'ACCRUED' }] }
}
test('BorrowDesk loan shows a dedicated buy-in settlement summary and confirmed payment states', async ({ page }) => {
  const detail = loan()
  await session(page, detail)
  await page.goto('http://localhost:5173/loans/41')
  const card = page.getByRole('region', { name: 'Buy-in loan closure', exact: true })
  await expect(card).toBeVisible()
  await expect(card).toContainText('CLOSE-BUYIN-42')
  await expect(card).toContainText('₹10,005')
  await expect(card).toContainText('₹995')
  await expect(card).toContainText('Payment due')
  await expect(card).toContainText('Awaiting payment')
  await expect(card).toContainText('not a rebate')
  await page.getByRole('button', { name: 'Activity', exact: true }).click()
  await expect(card).toBeVisible()
  detail.lending_fee[0].status = 'PAID'
  detail.rebate[0].status = 'PAID'
  await page.reload()
  await expect(card.getByText('Received records paid', { exact: true })).toHaveCount(2)
  await expect(card).toContainText('₹100 paid')
  await expect(card).toContainText('₹15 paid')
})

test('normal return closure does not display a buy-in closure card', async ({ page }) => {
  const detail = { ...loan(), buyinClosure: null, buyinClosurePending: false }
  await session(page, detail)
  await page.goto('http://localhost:5173/loans/41')
  await expect(page.locator('.lw-summary')).toContainText('CLOSED')
  await expect(page.getByRole('heading', { name: 'Buy-in loan closure', exact: true })).toHaveCount(0)
})

test('missing settlement data stays pending and appears on automatic refresh without invented amounts', async ({ page }) => {
  const detail = { ...loan(), buyinClosure: null, buyinClosurePending: true, lending_fee: [], rebate: [] }
  await session(page, detail)
  await page.goto('http://localhost:5173/loans/41')
  const card = page.getByRole('region', { name: 'Buy-in loan closure', exact: true })
  await expect(card).toContainText('Awaiting the lender’s settlement summary')
  await expect(card).not.toContainText('₹0')
  detail.buyinClosure = { ...summary }
  detail.buyinClosurePending = false
  await expect(card).toContainText('CLOSE-BUYIN-42', { timeout: 15000 })
  await expect(card).toContainText('Awaiting final billing')
  await expect(card).toContainText('Awaiting rebate confirmation')
})
