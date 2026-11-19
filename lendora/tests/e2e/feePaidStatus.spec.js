import { test, expect } from '@playwright/test'

const identity = (type, id) => ({ success: true, data: { organization: { type, name: 'Billing firm', platformOrganizationId: id }, user: { fullName: 'Admin' } } })
const reply = (route, data) => route.fulfill({ json: { success: true, data } })

test('BorrowDesk refreshes fee and rebate paid statuses without inferring payment from closure', async ({ page }) => {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: identity('BORROWER', 77) }))
  await page.route('http://localhost:8081/**', route => reply(route, []))
  const fees = [{ id: 1, loanId: 41, periodStart: '2026-11-18', periodEnd: '2026-11-18', feeAmount: 100, status: 'DUE' }]
  const rebates = [{ id: 2, loanId: 41, periodStart: '2026-11-18', periodEnd: '2026-11-18', accrualDate: '2026-11-18', amount: 10, status: 'ACCRUED' }]
  const accruals = [{ id: 3, loanId: 41, accrualDate: '2026-11-18', type: 'FEE', amount: 100 }, { id: 4, loanId: 41, accrualDate: '2026-11-18', type: 'REBATE', amount: 10 }]
  await page.route('**/organizations/77/fees?*', route => reply(route, { content: fees, totalPages: 1 }))
  await page.route('**/organizations/77/rebates?*', route => reply(route, { content: rebates, totalPages: 1 }))
  await page.route('**/organizations/77/accruals?*', route => reply(route, { content: accruals, totalPages: 1 }))
  await page.route('**/organizations/77/loans?*', route => reply(route, { content: [{ id: 41, status: 'CLOSED' }], totalPages: 1 }))
  await page.goto('http://localhost:5173/fees-rebates')
  const register = page.locator('.fd-register')
  await expect(register.locator('tbody tr')).toHaveCount(4)
  await expect(register.locator('.fd-badge').filter({ hasText: /^DUE$/ })).toHaveCount(2)
  await expect(register.locator('.fd-badge').filter({ hasText: /^ACCRUED$/ })).toHaveCount(2)
  fees[0].status = 'PAID'; rebates[0].status = 'PAID'
  await page.evaluate(() => window.dispatchEvent(new Event('focus')))
  await expect(register.locator('.fd-badge').filter({ hasText: /^PAID$/ })).toHaveCount(4)
})

test('LendDesk polls linked fee and rebate settlements while keeping the current view', async ({ page }) => {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: identity('LENDER', 37) }))
  await page.route('http://localhost:8084/**', route => reply(route, []))
  const rows = ['FEE', 'REBATE'].map((type, i) => ({ id: i + 1, key: `accruals:${i + 1}`, category: 'accruals', loanId: 41, type, borrower: 'Borrower firm', entryDate: '2026-11-18', amount: 100, currency: 'INR', status: type === 'FEE' ? 'DUE' : 'ACCRUED', settlementStatus: type === 'FEE' ? 'DUE' : 'ACCRUED' }))
  await page.route('**/organizations/37/lenddesk/fees-rebates/overview?*', route => reply(route, { records: { content: rows, totalPages: 1 }, totals: [], counts: { ALL: 2, FEE: 1, REBATE: 1, DUE: 2 }, eligibleLoans: [], loans: [] }))
  await page.goto('http://localhost:5175/fees-rebates')
  const table = page.locator('.al-table')
  await expect(table).toContainText('Due')
  await expect(table).toContainText('Accrued')
  rows.forEach(row => { row.status = 'PAID'; row.settlementStatus = 'PAID' })
  await expect(table.locator('tbody tr').filter({ hasText: 'Paid' })).toHaveCount(2, { timeout: 15000 })
  await expect(table).not.toContainText('Due')
})
