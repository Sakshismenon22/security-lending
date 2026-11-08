import { test, expect } from "@playwright/test";
test("manages a real portable broker and its records", async ({
  page,
  request,
}) => {
  const errors = [];
  page.on("pageerror", (e) => errors.push(e.message));
  try {
    await page.goto("/");
    await expect(
      page.getByRole("heading", { name: "Cluster overview" }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: /Initialize & start|Start broker/ })
      .click();
    await expect
      .poll(
        async () => (await (await request.get("/api/health")).json()).online,
        { timeout: 60000 },
      )
      .toBe(true);
    await page.getByRole("button", { name: "Refresh", exact: false }).click();
    await expect(
      page.getByRole("button", { name: "Create topic", exact: false }),
    ).toBeEnabled();
    await page
      .getByRole("button", { name: "Create topic", exact: false })
      .click();
    const dialog = page.getByRole("dialog");
    await dialog.getByLabel("Topic name").fill("relay.integration");
    await dialog.getByLabel("Partitions").fill("2");
    await dialog
      .getByRole("button", { name: "Create topic", exact: true })
      .click();
    await expect(dialog).not.toBeVisible();
    await page.getByRole("button", { name: "Browse ↗", exact: true }).click();
    await expect(
      page.getByRole("button", { name: "Publish message", exact: false }),
    ).toBeEnabled();
    await page
      .getByRole("button", { name: "Publish message", exact: false })
      .click();
    const payload = '{\n  "eventType": "LoanRequested",\n  "quantity": 125\n}';
    await dialog.getByLabel("Key").fill("loan-1042");
    await dialog.getByLabel("Message value").fill(payload);
    await dialog
      .getByRole("button", { name: "Publish message", exact: true })
      .click();
    await expect(
      page.getByText("loan-1042", { exact: true }).first(),
    ).toBeVisible({ timeout: 30000 });
    await page.getByText("loan-1042", { exact: true }).first().click();
    await expect(page.locator(".inspector pre").first()).toHaveText(payload);
    const snapshot = await (
      await request.get(
        "/api/messages?topic=relay.integration&limit=25&mode=earliest",
      )
    ).json();
    expect(snapshot.messages).toHaveLength(1);
    expect(snapshot.messages[0].value).toBe(payload);
    const invalid = await request.post("/api/topics", {
      data: { topic: "bad topic" },
    });
    expect(invalid.status()).toBe(400);
    await page
      .getByRole("button", { name: "Topics", exact: false })
      .first()
      .click();
    await page
      .getByRole("button", { name: "Set up lending topics", exact: false })
      .click();
    await expect(
      page
        .locator(".topic-link")
        .filter({ hasText: /^▤\s*borrower\.commands\.v1$/ }),
    ).toBeVisible({ timeout: 30000 });
    await page
      .getByRole("textbox", { name: "Search topics" })
      .fill("relay.integration");
    await expect(page.locator("tbody tr")).toHaveCount(1);
    await page.getByRole("textbox", { name: "Search topics" }).fill("");
    await page
      .getByRole("button", { name: "Overview", exact: false })
      .first()
      .click();
    await page.screenshot({
      path: "test-results/overview-desktop.png",
      fullPage: true,
    });
    await page.setViewportSize({ width: 390, height: 844 });
    await page.screenshot({
      path: "test-results/overview-mobile.png",
      fullPage: true,
    });
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true);
    await page
      .getByRole("button", { name: "Consumer groups", exact: false })
      .first()
      .click();
    await expect(
      page.getByRole("heading", { name: "Application subscriptions" }),
    ).toBeVisible();
    await page
      .getByRole("button", { name: "Broker logs", exact: false })
      .first()
      .click();
    await expect(page.locator(".log-view")).toContainText(/kafka/i);
    await page
      .getByRole("button", { name: "Settings", exact: false })
      .first()
      .click();
    await page
      .getByRole("button", { name: "Stop broker", exact: true })
      .click();
    await dialog.getByRole("button", { name: "Confirm stop" }).click();
    await expect
      .poll(
        async () => (await (await request.get("/api/health")).json()).online,
        { timeout: 40000 },
      )
      .toBe(false);
    expect(errors).toEqual([]);
  } finally {
    const h = await (await request.get("/api/health")).json();
    if (h.managed)
      await request.post("/api/kafka/stop", { data: {}, timeout: 40000 });
  }
});
