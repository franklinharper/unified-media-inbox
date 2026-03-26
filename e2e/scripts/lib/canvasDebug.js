import { chromium } from "@playwright/test";

export const APP_URL = process.env.APP_URL ?? "http://127.0.0.1:8081/";
export const VIEWPORT = { width: 1280, height: 720 };

export const AUTH = {
  email: { x: 640, y: 132 },
  password: { x: 640, y: 210 },
  signUpButton: { x: 960, y: 260 },
};

export const FEED = {
  emptyAddSourcesButton: { x: 78, y: 191 },
  addSourceFab: { x: 1235, y: 676 },
};

export const SOURCE_PICKER = {
  rssButton: { x: 68, y: 138 },
};

export const RSS_FORM = {
  urlField: { x: 640, y: 150 },
  addButton: { x: 90, y: 217 },
};

export async function withPage(run) {
  const browser = await chromium.launch({ channel: "chromium" });
  const page = await browser.newPage({ viewport: VIEWPORT });
  try {
    await page.goto(APP_URL, { waitUntil: "domcontentloaded", timeout: 60_000 });
    await page.waitForTimeout(5_000);
    await run(page);
  } finally {
    await browser.close();
  }
}

export async function click(page, point) {
  await page.mouse.click(point.x, point.y);
}

export async function activate(page, point, pauseMs = 300) {
  await click(page, point);
  await page.waitForTimeout(pauseMs);
  await click(page, point);
}

export async function typeInto(page, point, text) {
  await click(page, point);
  await page.keyboard.type(text);
}

export async function signUp(page, email, password) {
  await typeInto(page, AUTH.email, email);
  await typeInto(page, AUTH.password, password);
  await activate(page, AUTH.signUpButton);
  await page.waitForTimeout(5_000);
}

export async function openAddSourcePicker(page) {
  await click(page, FEED.emptyAddSourcesButton);
  await page.waitForTimeout(3_000);
}

export async function openRssForm(page) {
  await click(page, SOURCE_PICKER.rssButton);
  await page.waitForTimeout(3_000);
}

export async function addRssSource(page, url) {
  await typeInto(page, RSS_FORM.urlField, url);
  await activate(page, RSS_FORM.addButton);
  await page.waitForTimeout(5_000);
}
