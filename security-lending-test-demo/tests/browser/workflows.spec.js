import { test, expect } from "@playwright/test";
test(
  "registration, identities, lending actions, errors, payment headers and mobile layout",
  { tag: "@controlled-responses" },
  async ({ page }) => {
    const seen = [],
      errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    await page.route("**/bridge/**", async (route) => {
      const r = route.request(),
        path = new URL(r.url()).pathname;
      seen.push({
        path,
        method: r.method(),
        body: r.postDataJSON(),
        headers: r.headers(),
      });
      let data = { id: 31, status: "REQUESTED" },
        status = 200;
      if (path.endsWith("/organizations"))
        data =
          r.method() === "POST"
            ? {
                id: path.includes("/lender/") ? 22 : 11,
                name: "Demo organization",
              }
            : [
                {
                  id: path.includes("/lender/") ? 22 : 11,
                  name: "Demo organization",
                },
              ];
      if (path.endsWith("/users"))
        data = {
          content: [
            {
              id: 7,
              name: "Demo Admin",
              email: "admin@example.test",
              active: true,
            },
          ],
          totalPages: 1,
          page: 0,
          totalElements: 1,
        };
      if (path.endsWith("/locates") && r.method() === "POST") {
        status = 409;
        data = { title: "Conflict", detail: "Agreement is not active" };
      }
      await route.fulfill({
        status,
        contentType: "application/json",
        body: JSON.stringify(status === 200 ? { success: true, data } : data),
      });
    });
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Setup", exact: true }),
    ).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Login unavailable" }),
    ).toBeDisabled();
    await page
      .getByRole("button", { name: /Register borrower organization/ })
      .click();
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    await expect(
      page.getByLabel("Organization ID", { exact: true }),
    ).toHaveValue("11");
    await page
      .getByRole("button", { name: "Load organizations / users" })
      .click();
    await page.getByLabel("Test as user").selectOption("7");
    await page.getByRole("button", { name: "Lender", exact: true }).click();
    await page
      .getByRole("button", { name: /Register lender organization/ })
      .click();
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    await expect(
      page.getByLabel("Organization ID", { exact: true }),
    ).toHaveValue("22");
    await page.getByRole("button", { name: "Borrower", exact: true }).click();
    await expect(
      page.getByLabel("Organization ID", { exact: true }),
    ).toHaveValue("11");
    await page
      .getByRole("button", { name: /Agreements/ })
      .first()
      .click();
    await page.getByRole("button", { name: /Propose MSLA/ }).click();
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    const proposal = seen.find((r) => r.path.endsWith("/11/agreements"));
    expect(proposal.body.coverageRatio).toBe(1.05);
    expect(proposal.body.collateralRules[1].securityId).toBe(2);
    await page
      .getByRole("button", { name: /Locates & reservations/ })
      .first()
      .click();
    await page.getByRole("button", { name: /Request locate/ }).click();
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    await expect(page.locator(".response pre")).toContainText(
      "Agreement is not active",
    );
    await expect(page.locator(".status.failure")).toHaveText("409");
    await page
      .getByRole("button", { name: /Loans & returns/ })
      .first()
      .click();
    await page
      .getByRole("button", { name: /Request loan with collateral/ })
      .click();
    await page
      .getByLabel("Collateral", { exact: false })
      .fill(
        '[{"assetType":"SECURITY","securityId":2,"quantity":200,"cashAmount":0,"marginCallId":null}]',
      );
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    expect(
      seen.find((r) => r.path.endsWith("/11/loans")).body.collateral[0]
        .securityId,
    ).toBe(2);
    await page
      .getByRole("button", { name: /Billing & payments/ })
      .first()
      .click();
    await page
      .getByRole("button", { name: /Request lending fee payment/ })
      .click();
    await page.getByLabel("Resource ID").fill("19");
    await page
      .getByLabel("Idempotency-Key", { exact: false })
      .fill("DEMO-RETRY-KEY");
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    expect(
      seen.find((r) => r.path.endsWith("/fees/19/payments")).headers[
        "idempotency-key"
      ],
    ).toBe("DEMO-RETRY-KEY");
    await page.getByRole("button", { name: "Lender", exact: true }).click();
    await page
      .getByRole("button", { name: /Request cash-collateral rebate payment/ })
      .click();
    await page.getByLabel("Resource ID").fill("9");
    await page.getByLabel("Send this write").check();
    await page.getByRole("button", { name: "Send request" }).click();
    expect(
      seen.some((r) => r.path.endsWith("/22/rebates/9/payment-requests")),
    ).toBe(true);
    await page.getByRole("button", { name: /Guided workflow/ }).click();
    await expect(
      page.getByRole("heading", {
        name: "Follow a request across both platforms",
      }),
    ).toBeVisible();
    await page.getByLabel("Verify step 1", { exact: true }).check();
    await page.screenshot({
      path: "test-results/workflow-desktop.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: "test-results/workflow-mobile.png",
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    expect(errors).toEqual([]);
  },
);
