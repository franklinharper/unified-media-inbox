import { expect, type Page } from "@playwright/test";

export const APP_VIEWPORT = { width: 1280, height: 720 };
const AUTOMATION_PATH = "/?automationBridge=1";

const AUTH = {
  emailTestId: "e2e-auth-email",
  passwordTestId: "e2e-auth-password",
  signInTestId: "e2e-auth-sign-in",
  signUpTestId: "e2e-auth-sign-up",
};

const FEED = {
  refreshTestId: "e2e-feed-refresh",
  signOutTestId: "e2e-feed-sign-out",
  rssUrlTestId: "e2e-add-source-rss-url",
  rssSubmitTestId: "e2e-add-source-submit-rss",
};

export async function loadApp(page: Page): Promise<void> {
  await page.setViewportSize(APP_VIEWPORT);
  await page.goto(AUTOMATION_PATH, { waitUntil: "domcontentloaded" });
  await page.getByTestId("e2e-automation-bridge").waitFor({ state: "attached", timeout: 15_000 });
  await page.waitForTimeout(1_000);
}

export async function signUpThroughCanvas(page: Page, email: string, password: string): Promise<void> {
  const signUpResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/auth/sign-up") && response.request().method() === "POST",
  );

  await setBridgeField(page, AUTH.emailTestId, email);
  await setBridgeField(page, AUTH.passwordTestId, password);
  await pressBridgeButton(page, AUTH.signUpTestId);

  const signUpResponse = await signUpResponsePromise;
  expect(signUpResponse.status()).toBe(200);
  await expect(page.getByText("Feed")).toBeVisible({ timeout: 15_000 });
}

export async function signInThroughCanvas(page: Page, email: string, password: string): Promise<void> {
  const signInResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/auth/sign-in") && response.request().method() === "POST",
  );

  await setBridgeField(page, AUTH.emailTestId, email);
  await setBridgeField(page, AUTH.passwordTestId, password);
  await pressBridgeButton(page, AUTH.signInTestId);

  const signInResponse = await signInResponsePromise;
  expect(signInResponse.status()).toBe(200);
  await expect(page.getByText("Feed")).toBeVisible({ timeout: 15_000 });
}

export async function addRssSourceThroughCanvas(page: Page, url: string): Promise<FeedRefreshPayload> {
  const addSourceResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/sources") && response.request().method() === "POST",
  );

  await setBridgeField(page, FEED.rssUrlTestId, url);
  await pressBridgeButton(page, FEED.rssSubmitTestId);

  const addSourceResponse = await addSourceResponsePromise;
  expect(addSourceResponse.status()).toBe(201);

  const initialRefresh = await waitForFeedRefresh(page, async () => {
    await pressBridgeButton(page, FEED.refreshTestId);
  });
  if (initialRefresh.items.length > 0) return initialRefresh;

  return refreshFeedUntilItems(page, initialRefresh);
}

export async function signOutFromFeed(page: Page): Promise<void> {
  await pressBridgeButton(page, FEED.signOutTestId);
  await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible({ timeout: 15_000 });
}

export async function refreshFeedUntilItems(
  page: Page,
  initialRefresh?: FeedRefreshPayload,
): Promise<FeedRefreshPayload> {
  if (initialRefresh != null && initialRefresh.items.length > 0) return initialRefresh;

  var latest = initialRefresh;
  for (let attempt = 0; attempt < 4; attempt++) {
    await page.waitForTimeout(2_000);
    latest = await waitForFeedRefresh(page, async () => {
      await pressBridgeButton(page, FEED.refreshTestId);
    });
    if (latest.items.length > 0) return latest;
  }

  return latest ?? {
    status: 200,
    items: [],
    sourceStatuses: [],
  };
}

async function setBridgeField(page: Page, testId: string, value: string): Promise<void> {
  await page.getByTestId(testId).evaluate((element, nextValue) => {
    const input = element as HTMLInputElement;
    input.value = nextValue as string;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  }, value);
}

async function pressBridgeButton(page: Page, testId: string): Promise<void> {
  const button = page.getByTestId(testId);
  await expect(button).not.toBeDisabled({ timeout: 5_000 });
  await button.evaluate((element) => {
    (element as HTMLButtonElement).click();
  });
}


export async function expectRecentFeedItems(payload: FeedRefreshPayload): Promise<void> {
  expect(payload.status).toBe(200);
  expect(payload.items.length).toBeGreaterThan(0);
  expect(payload.items.some((item) => (item.title ?? "").trim().length > 0)).toBeTruthy();
  expect(
    payload.items.some((item) => /hacker news/i.test(item.source.displayName)),
  ).toBeTruthy();

  const twoWeeksAgo = Date.now() - 14 * 24 * 60 * 60 * 1000;
  expect(
    payload.items.some((item) => item.publishedAtEpochMillis >= twoWeeksAgo),
  ).toBeTruthy();
}

type FeedRefreshPayload = {
  status: number;
  items: Array<{
    title: string | null;
    publishedAtEpochMillis: number;
    source: { displayName: string };
  }>;
  sourceStatuses: Array<unknown>;
};

async function waitForFeedRefresh(
  page: Page,
  trigger: () => Promise<void>,
): Promise<FeedRefreshPayload> {
  const feedResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/feed/refresh?includeSeen=false") && response.request().method() === "POST",
  );
  await trigger();
  const response = await feedResponsePromise;
  const payload = (await response.json()) as Omit<FeedRefreshPayload, "status">;
  return {
    status: response.status(),
    ...payload,
  };
}
