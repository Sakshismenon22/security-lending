import { test, expect } from '@playwright/test'
const reply = (route, data) => route.fulfill({ json: { success: true, data } })
async function identity(page, type, id) {
  await page.route('http://localhost:8086/api/v1/auth/me', route => reply(route, { organization: { type, name: 'Ledger firm', platformOrganizationId: id }, user: { fullName: 'Admin' } }))
}

test('LendDesk ledger shows the execution fee included in the purchase amount', async ({ page }) => {
  await identity(page, 'LENDER', 37)
  await page.route('http://localhost:8084/**', route => reply(route, []))
  await page.route('**/organizations/37/lenddesk/ledger/overview?*', route => reply(route, {
    content: [{ id: 42, organizationId: 37, loanId: 9, borrowerName: 'Borrower firm', amount: 10005, executionFeeAmount: 5, type: 'BUY_IN_PURCHASE_PAYABLE', reference: 'TRADE-42', createdAt: '2026-11-19T09:00:00Z' }],
    page: 0, size: 25, totalElements: 1, totalPages: 1, totalAmount: 10005, options: { types: ['BUY_IN_PURCHASE_PAYABLE'], borrowers: [], loans: [9] }
  }))
  await page.goto('http://localhost:5175/ledger')
  const table = page.locator('.ref-main-table').first()
  await expect(table.getByRole('columnheader', { name: 'Execution fee (included)' })).toBeVisible()
  await expect(table.getByRole('cell', { name: '₹5.00', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'LED-000042', exact: true }).click()
  await expect(page.getByText(/It is not an additional charge/)).toBeVisible()
})

test('BorrowDesk funding ledger shows the fee allocation without another cash debit', async ({ page }) => {
  await identity(page, 'BORROWER', 77)
  await page.route('http://localhost:8081/**', route => reply(route, []))
  await page.route('**/organizations/77/account', route => reply(route, { available: 8000, blocked: 0 }))
  await page.route('**/organizations/77/transactions?*', route => reply(route, { content: [{ id: 51, loanId: 41, type: 'BUY_IN_EXECUTION_FEE', amount: 0, executionFeeAmount: 5, balanceAfter: 8000, reference: 'CLOSE-42', createdAt: new Date().toISOString() }], totalPages: 1 }))
  await page.route('**/organizations/77/loans?*', route => reply(route, { content: [{ id: 41, mslaId: 3, securityId: 7 }], totalPages: 1 }))
  await page.route('**/organizations/77/agreements?*', route => reply(route, { content: [{ id: 3, lenderId: 2 }], totalPages: 1 }))
  await page.route('**/organizations/77/lenders?*', route => reply(route, { content: [{ id: 2, name: 'Lender firm' }], totalPages: 1 }))
  await page.route('**/api/v1/securities', route => reply(route, [{ id: 7, symbol: 'INFY' }]))
  await page.goto('http://localhost:5173/funding')
  const table = page.locator('.funding-table-panel table')
  await expect(table.getByRole('columnheader', { name: 'Execution fee included (₹)' })).toBeVisible()
  const row = table.locator('tbody tr').filter({ hasText: 'CLOSE-42' })
  await expect(row).toContainText('Paid from collateral')
  await expect(row).toContainText('₹5.00')
  await expect(row).toContainText('Lender firm')
  await expect(row).toContainText('INFY')
  await expect(row.locator('td.debit')).toHaveText('-')
  await expect(row.locator('td.credit')).toHaveText('-')
  await expect(row).toContainText('₹8,000.00')
})
