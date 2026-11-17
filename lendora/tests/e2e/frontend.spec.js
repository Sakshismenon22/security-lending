import { test, expect } from "@playwright/test";
async function completeRegistration(page) {
  await page.getByLabel("Legal organization name").fill("Example Capital");
  await page.getByLabel("Institutional borrower", { exact: true }).check();
  await page.getByLabel("Registration number", { exact: true }).fill("REG-001");
  await page
    .getByLabel("Registered address", { exact: true })
    .fill("100 Market Road, Mumbai");
  await page.getByLabel("Full name", { exact: true }).fill("Example Admin");
  await page
    .getByLabel("Work email", { exact: true })
    .fill("admin@example.com");
  await page
    .getByLabel("Password", { exact: true })
    .fill("valid-passphrase-2026");
  await page
    .getByLabel("Confirm password", { exact: true })
    .fill("valid-passphrase-2026");
  await page.locator("#agreeTerms").check();
}
test("landing navigation and interactive workspace preview", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/Institutional securities lending/);
  await expect(
    page.getByRole("heading", { name: /Securities lending/ }),
  ).toBeVisible();
  await page.getByRole("tab", { name: "BorrowDesk", exact: true }).click();
  await expect(
    page
      .getByRole("tabpanel")
      .getByRole("heading", { name: "Locate requests" }),
  ).toBeVisible();
  await page.getByLabel("Search sample workspace").fill("INFY");
  await expect(page.getByRole("tabpanel").getByRole("row")).toHaveCount(2);
  await page.getByLabel("Search sample workspace").fill("not-present");
  await expect(
    page.getByText("No sample records match your search."),
  ).toBeVisible();
  await page.getByRole("tab", { name: "LendDesk", exact: true }).click();
  await expect(page.getByRole("tabpanel").getByRole("row")).toHaveCount(4);
  await page.getByRole("link", { name: "How it works", exact: true }).click();
  await expect(page).toHaveURL(/#how-it-works/);
  await page
    .getByRole("link", { name: "Create organization", exact: true })
    .first()
    .click();
  await expect(
    page.getByRole("heading", {
      name: "Create your organization",
      exact: true,
    }),
  ).toBeVisible();
});
test("signup validates fields and confirms creation only after the API succeeds", async ({
  page,
}) => {
  let body,
    calls = 0;
  await page.route("**/api/v1/organizations/register", async (route) => {
    calls++;
    body = route.request().postDataJSON();
    await new Promise((resolve) => setTimeout(resolve, 200));
    await route.fulfill({
      json: {
        success: true,
        data: {
          organization: { id: 1, name: "Example Capital", type: "BORROWER" },
        },
      },
    });
  });
  await page.goto("/signup");
  const submit = page.getByRole("button", {
    name: "Create organization & admin account",
    exact: true,
  });
  await submit.click();
  await expect(page.getByRole("alert")).toContainText(
    "Enter your legal organization name.",
  );
  await expect(page.getByRole("alert")).toBeFocused();
  expect(calls).toBe(0);
  await completeRegistration(page);
  await page
    .getByLabel("Confirm password", { exact: true })
    .fill("mismatching-password");
  await submit.click();
  await expect(page.locator("#confirmPassword-error")).toHaveText(
    "Passwords must match.",
  );
  expect(calls).toBe(0);
  await page
    .getByLabel("Confirm password", { exact: true })
    .fill("valid-passphrase-2026");
  await submit.click();
  await expect(
    page.getByRole("button", { name: "Creating organization…" }),
  ).toBeDisabled();
  await expect(page.getByLabel("Legal organization name")).toBeDisabled();
  await expect(
    page.getByRole("heading", { name: "Your organization is ready." }),
  ).toBeVisible();
  expect(calls).toBe(1);
  expect(body.organization.type).toBe("BORROWER");
  expect(body.administrator.password).toBe("valid-passphrase-2026");
  expect(body).not.toHaveProperty("confirmPassword");
  expect(body.acceptedTerms).toBe(true);
  await page.getByRole("link", { name: "Continue to log in" }).click();
  await expect(page.getByLabel("Work email")).toHaveValue("admin@example.com");
  expect(
    await page.evaluate(() => Object.values(localStorage).join(" ")),
  ).not.toContain("valid-passphrase-2026");
});
test("login shows API errors, supports visibility and remember-me, and permits a retry", async ({
  page,
}) => {
  await page.route("http://localhost:5175/**", route=>route.fulfill({contentType:"text/html",body:"<h1>LendDesk</h1>"}));
  let attempts = 0,
    body;
  await page.route("**/api/v1/auth/login", async (route) => {
    attempts++;
    body = route.request().postDataJSON();
    await route.fulfill(
      attempts === 1
        ? {
            status: 401,
            json: {
              success: false,
              message: "Email or password is incorrect.",
            },
          }
        : {
            json: {
              success: true,
              data: {
                organization: { name: "Example Capital", type: "LENDER" },
              },
            },
          },
    );
  });
  await page.goto("/login");
  await page
    .getByLabel("Work email", { exact: true })
    .fill("admin@example.com");
  await page.getByLabel("Password", { exact: true }).fill("existing-password");
  await page
    .getByRole("button", { name: "Show password", exact: true })
    .click();
  await expect(page.getByLabel("Password", { exact: true })).toHaveAttribute(
    "type",
    "text",
  );
  await page
    .getByRole("button", { name: "Hide password", exact: true })
    .click();
  await page.getByLabel("Remember me").check();
  await page.getByRole("button", { name: "Log in", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText(
    "Email or password is incorrect.",
  );
  await expect(
    page.getByRole("heading", { name: "Welcome back." }),
  ).toHaveCount(0);
  await page.getByRole("button", { name: "Log in", exact: true }).click();
  await expect(page).toHaveURL(/^http:\/\/localhost:5175\/?$/);
  expect(body.rememberMe).toBe(true);
});
test("password recovery validates email and handles service failure and success", async ({
  page,
}) => {
  let attempts = 0;
  await page.route("**/api/v1/auth/password-reset", async (route) => {
    attempts++;
    expect(route.request().postDataJSON().email).toBe("admin@example.com");
    await route.fulfill(
      attempts === 1
        ? { status: 503, json: { success: false } }
        : { json: { success: true, data: {} } },
    );
  });
  await page.goto("/forgot-password");
  await page.getByRole("button", { name: "Send reset instructions" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Enter a valid work email address.",
  );
  expect(attempts).toBe(0);
  await page.getByLabel("Work email").fill("admin@example.com");
  await page.getByRole("button", { name: "Send reset instructions" }).click();
  await expect(page.getByRole("alert")).toContainText(
    "This service is currently unavailable.",
  );
  await page.getByRole("button", { name: "Send reset instructions" }).click();
  await expect(
    page.getByRole("heading", { name: "Check your inbox" }),
  ).toBeVisible();
});
test("unavailable or malformed API responses never report account creation", async ({
  page,
}) => {
  let malformed = false;
  await page.route("**/api/v1/organizations/register", (route) =>
    route.fulfill(
      malformed ? { json: {} } : { status: 404, json: { success: false } },
    ),
  );
  await page.goto("/signup");
  await completeRegistration(page);
  await page
    .getByRole("button", { name: "Create organization & admin account" })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "This service is currently unavailable.",
  );
  malformed = true;
  await page
    .getByRole("button", { name: "Create organization & admin account" })
    .click();
  await expect(page.getByRole("alert")).toContainText(
    "We could not complete your request.",
  );
  await expect(
    page.getByRole("heading", { name: "Your organization is ready." }),
  ).toHaveCount(0);
});
for (const width of [360, 390, 768]) {
  test(`responsive routes and mobile navigation at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 844 });
    for (const path of ["/", "/login", "/signup"]) {
      await page.goto(path);
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBeLessThanOrEqual(width + 1);
      await expect(page.getByRole("main")).toBeVisible();
    }
    await page.goto("/");
    await page.getByRole("button", { name: "Open navigation" }).click();
    await page.getByRole("link", { name: "How it works", exact: true }).click();
    await expect(page).toHaveURL(/#how-it-works/);
    await expect(
      page.getByRole("button", { name: "Open navigation" }),
    ).toBeVisible();
    await expect(
      page.getByRole("navigation", { name: "Main navigation" }),
    ).not.toBeVisible();
  });
}
test("policy links, register alias and unknown routes resolve", async ({
  page,
}) => {
  await page.goto("/register");
  await expect(page).toHaveURL(/\/signup$/);
  await page.goto("/privacy");
  await expect(
    page.getByRole("heading", { name: "Privacy information", exact: true }),
  ).toBeVisible();
  await page.goto("/terms");
  await expect(
    page.getByRole("heading", { name: "Terms of use", exact: true }),
  ).toBeVisible();
  await page.goto("/not-a-page");
  await expect(
    page.getByRole("heading", { name: "Let’s get you back." }),
  ).toBeVisible();
});

test('borrower login opens BorrowDesk', async ({ page }) => {
  await page.route('http://localhost:5173/**', route=>route.fulfill({contentType:'text/html',body:'<h1>BorrowDesk</h1>'}))
  await page.route('**/api/v1/auth/login', route=>route.fulfill({json:{success:true,data:{organization:{type:'BORROWER',name:'Example Borrower'}}}}))
  await page.goto('/login')
  await page.getByLabel('Work email',{exact:true}).fill('borrower@example.com')
  await page.getByLabel('Password',{exact:true}).fill('valid-passphrase-2026')
  await page.getByRole('button',{name:'Log in',exact:true}).click()
  await expect(page).toHaveURL(/^http:\/\/localhost:5173\/?$/)
})

test('LendDesk pins the logged-in lender organization and supports logout', async ({ page }) => {
  const calls=[]
  await page.addInitScript(()=>{localStorage.setItem('lenddesk-data-mode','demo');localStorage.setItem('lenddesk-organization-id','999')})
  await page.route('http://localhost:8086/api/v1/auth/**', route=>route.fulfill({json:{success:true,data:route.request().method()==='POST'?{loggedOut:true}:{organization:{type:'LENDER',name:'Actual lender',platformOrganizationId:37},user:{fullName:'Actual Admin'}}}}))
  await page.route('http://localhost:8084/**', route=>{
    calls.push(new URL(route.request().url()).pathname)
    const data=route.request().url().includes('/ledger/overview')?{content:[],page:0,size:25,totalElements:0,totalPages:0,totalAmount:0,options:{types:[],borrowers:[],loans:[]}}:[]
    return route.fulfill({json:{success:true,data}})
  })
  await page.goto('http://localhost:5175/ledger')
  await expect(page.getByText('Actual lender · Actual Admin')).toBeVisible()
  await expect(page.getByText('0–0 of 0 entries',{exact:true})).toBeVisible()
  expect(calls.some(path=>path.includes('/organizations/37/'))).toBe(true)
  expect(calls.some(path=>path.includes('/organizations/999/'))).toBe(false)
  await page.getByRole('button',{name:'Log out',exact:true}).click()
  await expect(page).toHaveURL(/localhost:5176\/login$/)
})

test('BorrowDesk binds API URLs after resolving the logged-in organization', async ({ page }) => {
  const calls=[], loan={id:1,security_id:1,msla_id:1,outstanding_quantity:10,original_quantity:10,status:'ACTIVE'}
  await page.route('http://localhost:8086/api/v1/auth/me', route=>route.fulfill({json:{success:true,data:{organization:{type:'BORROWER',name:'Actual borrower',platformOrganizationId:38},user:{fullName:'Borrower Admin'}}}}))
  await page.route('http://localhost:8081/**', route=>{
    const path=new URL(route.request().url()).pathname;calls.push(path)
    let data=[]
    if(path.endsWith('/loans'))data={content:[loan]}
    else if(path.endsWith('/loans/1'))data={loan,collateral_position:[]}
    else if(path.endsWith('/securities'))data=[{id:1,symbol:'INFY',price:100}]
    return route.fulfill({json:{success:true,data}})
  })
  await page.goto('http://localhost:5173/loans')
  await expect(page.getByText('Actual borrower · Borrower Admin')).toBeVisible()
  await expect(page.getByRole('heading',{name:'Active Loans',exact:true})).toBeVisible()
  expect(calls.some(path=>path.includes('/organizations/38/'))).toBe(true)
  expect(calls.some(path=>path.includes('/organizations/1/'))).toBe(false)
})

test('wrong workspace and missing sessions redirect without loading tenant data', async ({ page }) => {
  let unauthenticated=false
  await page.route('http://localhost:8086/api/v1/auth/me', route=>route.fulfill(unauthenticated?{status:401,json:{success:false}}:{json:{success:true,data:{organization:{type:'LENDER',platformOrganizationId:37},user:{fullName:'Admin'}}}}))
  await page.route('http://localhost:5175/**', route=>route.fulfill({contentType:'text/html',body:'<h1>LendDesk</h1>'}))
  await page.goto('http://localhost:5173/loans')
  await expect(page).toHaveURL(/^http:\/\/localhost:5175\/?$/)
  unauthenticated=true
  await page.goto('http://localhost:5173/loans')
  await expect(page).toHaveURL(/localhost:5176\/login$/)
})
