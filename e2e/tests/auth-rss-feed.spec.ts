import { test } from "@playwright/test";
import {
  addRssSourceThroughCanvas,
  expectRecentFeedItems,
  loadApp,
  refreshFeedUntilItems,
  signInThroughCanvas,
  signOutFromFeed,
  signUpThroughCanvas,
} from "./support/canvasApp";

test("user can sign up, add HNRSS frontpage, sign out, sign back in, and load recent feed items", async ({ page }) => {
  const uniqueEmail = `playwright-hnrss-${Date.now()}@example.com`;
  const password = "secret123";

  await loadApp(page);
  await signUpThroughCanvas(page, uniqueEmail, password);

  await addRssSourceThroughCanvas(page, "https://hnrss.org/frontpage");
  await expectRecentFeedItems(page);

  await signOutFromFeed(page);
  await signInThroughCanvas(page, uniqueEmail, password);

  await refreshFeedUntilItems(page);
  await expectRecentFeedItems(page);
});
