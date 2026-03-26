import { signUp, withPage } from "./lib/canvasDebug.js";

const email = `debug-signup-${Date.now()}@example.com`;

await withPage(async (page) => {
  const responses = [];
  page.on("response", async (response) => {
    if (!response.url().includes("/api/auth/sign-up")) return;
    responses.push({
      url: response.url(),
      status: response.status(),
      body: await response.text().catch(() => ""),
    });
  });

  await signUp(page, email, "secret123");
  await page.screenshot({ path: "debug-signup.png" });
  console.log(JSON.stringify({ email, responses }, null, 2));
});
