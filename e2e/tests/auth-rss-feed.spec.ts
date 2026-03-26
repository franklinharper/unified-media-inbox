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

  const feedResponse = await addRssSourceThroughCanvas(page, "https://hnrss.org/frontpage");
  await expectRecentFeedItems(feedResponse);

  await signOutFromFeed(page);
  await signInThroughCanvas(page, uniqueEmail, password);

  const refreshedFeed = await refreshFeedUntilItems(page);
  await expectRecentFeedItems(refreshedFeed);
});
