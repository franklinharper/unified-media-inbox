import { expect, type Page } from "@playwright/test";

export const APP_VIEWPORT = { width: 1280, height: 720 };

const AUTH = {
  email: { x: 640, y: 132 },
  password: { x: 640, y: 210 },
  signInButton: { x: 320, y: 260 },
  signUpButton: { x: 960, y: 260 },
};

const FEED = {
  emptyAddSourcesButton: { x: 78, y: 191 },
  refreshButton: { x: 1100, y: 38 },
  signOutButton: { x: 1195, y: 38 },
};

const ADD_SOURCE = {
  rssTypeButton: { x: 68, y: 138 },
  rssUrlField: { x: 640, y: 150 },
  addSourceButton: { x: 90, y: 217 },
};

export async function loadApp(page: Page): Promise<void> {
  await page.setViewportSize(APP_VIEWPORT);
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await page.waitForTimeout(5_000);
}

export async function signUpThroughCanvas(page: Page, email: string, password: string): Promise<void> {
  const signUpResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/auth/sign-up") && response.request().method() === "POST",
  );

  await replaceTextInField(page, AUTH.email, email);
  await replaceTextInField(page, AUTH.password, password);
  await activateCanvasButton(page, AUTH.signUpButton);

  const signUpResponse = await signUpResponsePromise;
  expect(signUpResponse.status()).toBe(200);
  await expect(page.getByText("Feed")).toBeVisible({ timeout: 15_000 });
}

export async function signInThroughCanvas(page: Page, email: string, password: string): Promise<void> {
  const signInResponsePromise = page.waitForResponse((response) =>
    response.url().includes("/api/auth/sign-in") && response.request().method() === "POST",
  );

  await replaceTextInField(page, AUTH.email, email);
  await replaceTextInField(page, AUTH.password, password);
  await activateCanvasButton(page, AUTH.signInButton);

  const signInResponse = await signInResponsePromise;
  expect(signInResponse.status()).toBe(200);
  await expect(page.getByText("Feed")).toBeVisible({ timeout: 15_000 });
}

export async function addRssSourceThroughCanvas(page: Page, url: string): Promise<FeedRefreshPayload> {
  await page.mouse.click(FEED.emptyAddSourcesButton.x, FEED.emptyAddSourcesButton.y);
  await page.waitForTimeout(3_000);
  await page.mouse.click(ADD_SOURCE.rssTypeButton.x, ADD_SOURCE.rssTypeButton.y);
  await page.waitForTimeout(3_000);
  await replaceTextInField(page, ADD_SOURCE.rssUrlField, url);
  const initialRefresh = await waitForFeedRefresh(page, async () => {
    await activateCanvasButton(page, ADD_SOURCE.addSourceButton);
  });
  if (initialRefresh.items.length > 0) return initialRefresh;

  return refreshFeedUntilItems(page, initialRefresh);
}

export async function signOutFromFeed(page: Page): Promise<void> {
  await activateCanvasButton(page, FEED.signOutButton);
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
      await page.mouse.click(FEED.refreshButton.x, FEED.refreshButton.y);
    });
    if (latest.items.length > 0) return latest;
  }

  return latest ?? {
    status: 200,
    items: [],
    sourceStatuses: [],
  };
}

async function activateCanvasButton(
  page: Page,
  point: { x: number; y: number },
): Promise<void> {
  await page.mouse.click(point.x, point.y);
  await page.waitForTimeout(300);
  await page.mouse.click(point.x, point.y);
}

async function replaceTextInField(
  page: Page,
  point: { x: number; y: number },
  text: string,
): Promise<void> {
  await page.mouse.click(point.x, point.y);
  await page.keyboard.press("Meta+A");
  await page.keyboard.press("Backspace");
  await page.keyboard.type(text);
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
