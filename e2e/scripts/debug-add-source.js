import {
  addRssSource,
  openAddSourcePicker,
  openRssForm,
  signUp,
  withPage,
} from "./lib/canvasDebug.js";

const email = `debug-add-source-${Date.now()}@example.com`;
const rssUrl = process.env.RSS_URL ?? "https://hnrss.org/frontpage";

await withPage(async (page) => {
  const responses = [];
  page.on("response", async (response) => {
    if (
      response.url().includes("/api/sources") ||
      response.url().includes("/api/feed/refresh?includeSeen=false")
    ) {
      responses.push({
        url: response.url(),
        status: response.status(),
        body: await response.text().catch(() => ""),
      });
    }
  });

  await signUp(page, email, "secret123");
  await openAddSourcePicker(page);
  await openRssForm(page);
  await addRssSource(page, rssUrl);

  await page.screenshot({ path: "debug-add-source.png" });
  console.log(JSON.stringify({ email, rssUrl, responses }, null, 2));
});
