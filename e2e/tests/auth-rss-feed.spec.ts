import { test } from "@playwright/test";
import {
  addRssSourceThroughCanvas,
  expectRecentFeedItems,
  loadApp,
  signUpThroughCanvas,
} from "./support/canvasApp";

test("user can sign up, add HNRSS frontpage, and load recent feed items", async ({ page }) => {
  const uniqueEmail = `playwright-hnrss-${Date.now()}@example.com`;

  await loadApp(page);
  await signUpThroughCanvas(page, uniqueEmail, "secret123");

  const feedResponse = await addRssSourceThroughCanvas(page, "https://hnrss.org/frontpage");
  await expectRecentFeedItems(feedResponse);
});
