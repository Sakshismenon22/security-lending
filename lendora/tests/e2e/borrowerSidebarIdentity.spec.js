import { test, expect } from '@playwright/test'
async function setup(page, name, role, accountRole='ADMIN') {
  await page.route('http://localhost:8086/api/v1/auth/me', route => route.fulfill({ json: { success:true, data:{organization:{type:'BORROWER',name:'Borrower firm',platformOrganizationId:77},user:{fullName:name,email:'member@example.com',role:accountRole}} } }))
  await page.route('http://localhost:8081/**', route => route.fulfill({json:{success:true,data:[]}}))
  await page.route('**/organizations/77/admin-access', route => route.fulfill({json:{success:true,data:{role,admin:role==='ADMIN',userId:9}}}))
}

test('sidebar uses the signed-in name and actual workspace role, including after focus refresh', async ({ page }) => {
  await setup(page,'Alice Morgan','TRADER')
  await page.goto('http://localhost:5173/funding')
  const profile=page.getByRole('group',{name:'Signed-in profile'})
  await expect(profile).toContainText('Alice Morgan')
  await expect(profile.locator('small')).toHaveText('Trader')
  await expect(profile.locator('b')).toHaveText('AM')
  await expect(profile).not.toContainText('Niranjan V.')
  await page.route('**/organizations/77/admin-access',route=>route.fulfill({json:{success:true,data:{role:'READ_ONLY',admin:false,userId:9}}}))
  await page.evaluate(()=>window.dispatchEvent(new Event('focus')))
  await expect(profile.locator('small')).toHaveText('Read only')
  await page.getByRole('button',{name:'Collapse navigation',exact:true}).click()
  await expect(profile).toHaveAttribute('title','Alice Morgan · Read only')
  await expect(profile.locator('b')).toHaveText('AM')
})

test('sidebar shows the current administrator and does not invent a role if verification fails',async({page})=>{
  await setup(page,'Priya Shah','ADMIN')
  await page.goto('http://localhost:5173/funding')
  const profile=page.getByRole('group',{name:'Signed-in profile'})
  await expect(profile).toContainText('Priya Shah')
  await expect(profile.locator('small')).toHaveText('Administrator')
  await expect(profile.locator('b')).toHaveText('PS')
  await page.route('**/organizations/77/admin-access',route=>route.fulfill({status:503,json:{detail:'Unavailable'}}))
  await page.evaluate(()=>window.dispatchEvent(new Event('focus')))
  await expect(profile.locator('small')).toHaveText('Role unavailable')
  await expect(profile).toContainText('Priya Shah')
})
