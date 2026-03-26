import { test } from "@playwright/test";
import {
  addRssSourceThroughCanvas,
  expectFixtureFeedItems,
  loadApp,
  refreshFeedUntilItems,
  signInThroughCanvas,
  signOutFromFeed,
  signUpThroughCanvas,
} from "./support/canvasApp";

const FIXTURE_RSS_URL = "http://127.0.0.1:9090/feeds/hn-frontpage.xml";
const FIXTURE_SOURCE_NAME = "Hacker News Front Page";
const FIXTURE_TITLES = [
  "Launch HN: Deterministic Feed Fixtures",
  "Ask HN: How do you de-flake end-to-end tests?",
];

test("user can sign up, add a fixture-backed Hacker News feed, sign out, sign back in, and load expected items", async ({ page }) => {
  const uniqueEmail = `playwright-hnrss-${Date.now()}@example.com`;
  const password = "secret123";

  await loadApp(page);
  await signUpThroughCanvas(page, uniqueEmail, password);

  await addRssSourceThroughCanvas(page, FIXTURE_RSS_URL);
  await expectFixtureFeedItems(page, FIXTURE_SOURCE_NAME, FIXTURE_TITLES);

  await signOutFromFeed(page);
  await signInThroughCanvas(page, uniqueEmail, password);

  await refreshFeedUntilItems(page);
  await expectFixtureFeedItems(page, FIXTURE_SOURCE_NAME, FIXTURE_TITLES);
});
