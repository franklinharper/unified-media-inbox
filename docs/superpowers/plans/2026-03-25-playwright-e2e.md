# Playwright E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Playwright browser automation test that starts the local server and JS web client automatically and verifies signup through the real browser UI.

**Architecture:** Create a separate root-level `e2e` Node project with Playwright config and a single signup spec. Use Playwright `webServer` process management to boot the Ktor server and JS web dev server, then run Chromium against `http://127.0.0.1:8081`.

**Tech Stack:** Playwright, Node.js, TypeScript, Gradle, Ktor, Compose Multiplatform JS

---

### Task 1: E2E Project Skeleton

**Files:**
- Create: `e2e/package.json`
- Create: `e2e/playwright.config.ts`
- Create: `e2e/tsconfig.json`

- [ ] Step 1: Write the config and package skeleton for Playwright with automatic server/web startup
- [ ] Step 2: Install dependencies and run Playwright config validation
- [ ] Step 3: Verify the runner can boot both local services
- [ ] Step 4: Commit the e2e project scaffold

### Task 2: Stable UI Selectors

**Files:**
- Modify: `src/composeApp/src/commonMain/kotlin/com/franklinharper/social/media/client/ui/FeedScreen.kt`

- [ ] Step 1: Add a stable authenticated-shell selector if the existing screen surface is not reliable enough
- [ ] Step 2: Re-run the narrow compose test/compile verification
- [ ] Step 3: Commit selector updates

### Task 3: Signup Browser Test

**Files:**
- Create: `e2e/tests/auth-signup.spec.ts`

- [ ] Step 1: Write the first failing Playwright signup test
- [ ] Step 2: Run it and capture the first real browser failure
- [ ] Step 3: Fix any selector or timing issues without broadening scope
- [ ] Step 4: Re-run until green
- [ ] Step 5: Commit the signup e2e test

### Task 4: Docs And Command

**Files:**
- Modify: `src/README.md`

- [ ] Step 1: Document the Playwright install/run command
- [ ] Step 2: Run the full e2e command once more as final verification
- [ ] Step 3: Commit docs and final verification
