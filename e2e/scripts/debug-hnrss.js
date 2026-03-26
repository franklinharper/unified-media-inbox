import {
  addRssSource,
  openAddSourcePicker,
  openRssForm,
  signUp,
  withPage,
} from "./lib/canvasDebug.js";

const email = `debug-hnrss-${Date.now()}@example.com`;
const rssUrl = "https://hnrss.org/frontpage";

await withPage(async (page) => {
  const responses = [];
  page.on("response", async (response) => {
    if (!response.url().includes("/api/feed/refresh?includeSeen=false")) return;
    responses.push({
      url: response.url(),
      status: response.status(),
      body: await response.text().catch(() => ""),
    });
  });

  await signUp(page, email, "secret123");
  await openAddSourcePicker(page);
  await openRssForm(page);
  await addRssSource(page, rssUrl);

  await page.screenshot({ path: "debug-hnrss.png" });
  console.log(JSON.stringify({ email, rssUrl, responses }, null, 2));
});
