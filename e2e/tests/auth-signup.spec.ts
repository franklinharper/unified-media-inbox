import { expect, test } from "@playwright/test";

test.skip(
  "Compose JS auth UI currently renders through a canvas without stable browser selectors; enable once the web surface exposes a reliable automation path.",
);

test("user can sign up through the web client", async ({ page }) => {
  const uniqueEmail = `playwright-${Date.now()}@example.com`;

  await page.goto("/");

  await page.getByTestId("login-email-field").locator("input").fill(uniqueEmail);
  await page.getByTestId("login-password-field").locator("input").fill("secret123");
  await page.getByTestId("login-sign-up-button").click();

  await expect(page.getByText("Feed")).toBeVisible();
  await expect(page.getByTestId("feed-add-source-fab")).toBeVisible();
});
